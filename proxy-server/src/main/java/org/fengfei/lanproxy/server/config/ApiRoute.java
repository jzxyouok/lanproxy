package org.fengfei.lanproxy.server.config;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.fengfei.lanproxy.server.handlers.HttpRequestHandler.RequestHandler;

public class ApiRoute {
    private static Map<String, RequestHandler> routes = new ConcurrentHashMap<String, RequestHandler>();
    private static Map<String, RequestHandler> middlewares = new ConcurrentHashMap<String, RequestHandler>();
}
