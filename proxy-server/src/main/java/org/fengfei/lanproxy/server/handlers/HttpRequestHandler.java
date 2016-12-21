package org.fengfei.lanproxy.server.handlers;

import java.io.File;
import java.io.RandomAccessFile;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.fengfei.lanproxy.common.JsonUtil;
import org.fengfei.lanproxy.common.MimeType;

import com.google.gson.reflect.TypeToken;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.DefaultFileRegion;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpHeaders.Names;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedNioFile;

public class HttpRequestHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final String PAGE_FOLDER = System.getProperty("app.home", System.getProperty("user.dir"))
            + "/webpages";

    private static final String SERVER_VS = "LPS-0.1";

    private static Map<String, RequestHandler> httpRoute = new ConcurrentHashMap<String, RequestHandler>();

    static {
        httpRoute.put("/v1/client/add", new RequestHandler() {

            @Override
            public ResponseInfo request(FullHttpRequest request) {
                byte[] buf = new byte[request.content().readableBytes()];
                request.content().readBytes(buf);
                String content = new String(buf);
                Map<String, String> params = JsonUtil.json2object(content, new TypeToken<Map<String, String>>() {
                });
                if (params == null) {
                    return new ResponseInfo(40000, "error request body");
                }
                return new ResponseInfo("ok");
            }
        });
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        if (request.getMethod() != HttpMethod.POST) {
            outputPages(ctx, request);
            return;
        }
        URI uri = new URI(request.getUri());
        RequestHandler handler = httpRoute.get(uri.getPath());
        ResponseInfo responseInfo = null;
        if (handler != null) {
            responseInfo = handler.request(request);
        } else {
            responseInfo = new ResponseInfo(ResponseInfo.CODE_API_NOT_FOUND, "api not found");
        }
        outputContent(ctx, request, responseInfo.getCode() / 100, JsonUtil.object2json(responseInfo),
                "application/json");
    }

    private void outputContent(ChannelHandlerContext ctx, FullHttpRequest request, int code, String content,
            String mimeType) {

        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.valueOf(code),
                Unpooled.wrappedBuffer(content.getBytes(Charset.forName("UTF-8"))));
        response.headers().set(Names.CONTENT_TYPE, mimeType);
        response.headers().set(Names.CONTENT_LENGTH, response.content().readableBytes());
        response.headers().set(Names.SERVER, SERVER_VS);
        ChannelFuture future = ctx.writeAndFlush(response);
        if (!HttpHeaders.isKeepAlive(request)) {
            future.addListener(ChannelFutureListener.CLOSE);
        }

    }

    private void outputPages(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        HttpResponseStatus status = HttpResponseStatus.OK;
        URI uri = new URI(request.getUri());
        String uriPath = uri.getPath();
        uriPath = uriPath.equals("/") ? "/index.html" : uriPath;
        String path = PAGE_FOLDER + uriPath;
        File rfile = new File(path);
        if (!rfile.exists()) {
            status = HttpResponseStatus.NOT_FOUND;
            outputContent(ctx, request, status.code(), status.toString(), "text/html");
            return;
        }
        if (HttpHeaders.is100ContinueExpected(request)) {
            send100Continue(ctx);
        }
        String mimeType = MimeType.getMimeType(MimeType.parseSuffix(path));
        RandomAccessFile file = new RandomAccessFile(rfile, "r");
        HttpResponse response = new DefaultHttpResponse(request.getProtocolVersion(), status);
        response.headers().set(HttpHeaders.Names.CONTENT_TYPE, mimeType);
        boolean keepAlive = HttpHeaders.isKeepAlive(request);
        if (keepAlive) {
            response.headers().set(HttpHeaders.Names.CONTENT_LENGTH, file.length());
            response.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
        }
        response.headers().set(Names.SERVER, SERVER_VS);
        ctx.write(response);

        if (ctx.pipeline().get(SslHandler.class) == null) {
            ctx.write(new DefaultFileRegion(file.getChannel(), 0, file.length()));
        } else {
            ctx.write(new ChunkedNioFile(file.getChannel()));
        }
        ChannelFuture future = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
        if (!keepAlive) {
            future.addListener(ChannelFutureListener.CLOSE);
        }
        file.close();

    }

    private static void send100Continue(ChannelHandlerContext ctx) {
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE);
        ctx.writeAndFlush(response);
    }

    public interface RequestHandler {

        ResponseInfo request(FullHttpRequest request);
    }

    public static class ResponseInfo {

        public static final int CODE_OK = 20000;

        public static final int CODE_INVILID_PARAMS = 40000;

        public static final int CODE_API_NOT_FOUND = 40400;

        public static final int CODE_SYSTEM_ERROR = 50000;

        private int code;
        private String message;
        private Object data;

        public ResponseInfo(int code, String message, Object data) {
            this.code = code;
            this.message = message;
            this.data = data;
        }

        public ResponseInfo(int code, String message) {
            this(code, message, new String[] {});
        }

        public ResponseInfo(Object data) {
            this(ResponseInfo.CODE_OK, "success", data);
        }

        public int getCode() {
            return code;
        }

        public void setCode(int code) {
            this.code = code;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public Object getData() {
            return data;
        }

        public void setData(Object data) {
            this.data = data;
        }

    }
}
