package org.fengfei.lanproxy.server.handlers;

import java.io.File;
import java.io.RandomAccessFile;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.fengfei.lanproxy.common.JsonUtil;
import org.fengfei.lanproxy.common.MimeType;

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
import io.netty.handler.codec.http.HttpHeaders.Values;
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

    private static Map<String, RequestHandler> httpRoute = new ConcurrentHashMap<String, RequestHandler>();

    static {
        httpRoute.put("/v1/client/add", new RequestHandler() {

            @Override
            public ResponseInfo request(FullHttpRequest request) {
                // TODO Auto-generated method stub
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
            responseInfo = new ResponseInfo(40400, "api not found");
        }

        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                HttpResponseStatus.valueOf(responseInfo.getCode() / 100),
                Unpooled.wrappedBuffer(JsonUtil.object2json(responseInfo).getBytes("UTF-8")));
        response.headers().set(Names.CONTENT_TYPE, "application/json");
        response.headers().set(Names.CONTENT_LENGTH, response.content().readableBytes());
        if (HttpHeaders.isKeepAlive(request)) {
            response.headers().set(Names.CONNECTION, Values.KEEP_ALIVE);
        }
        ctx.write(response);
        ctx.flush();
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
            rfile = new File(PAGE_FOLDER + "/index.html");
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

        private int code;
        private String message;
        private Object dara;

        public ResponseInfo(int code, String message, Object data) {
            this.code = code;
            this.message = message;
            this.dara = data;
        }

        public ResponseInfo(int code, String message) {
            this(code, message, new String[] {});
        }

        public ResponseInfo(Object data) {
            this(20000, "success", data);
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

        public Object getDara() {
            return dara;
        }

        public void setDara(Object dara) {
            this.dara = dara;
        }

    }
}
