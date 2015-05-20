package io.fabric8.jube.websocket;

import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import io.fabric8.jube.apimaster.ApiMasterService;
import org.apache.cxf.cdi.CXFCdiServlet;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.websocket.api.extensions.Extension;
import org.eclipse.jetty.websocket.servlet.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class WatchWebSocketServlet extends WebSocketServlet {

    private static final transient Logger LOG = LoggerFactory.getLogger(WatchWebSocketServlet.class);

    final CXFCdiServlet target;
    final ServletHolder holder;
    @Inject
    private ApiMasterService service;

    public WatchWebSocketServlet() {
        target = new CXFCdiServlet();
        holder = new ServletHolder(target);
        holder.setInitParameter("service-list-path", "/cxf/servicesList");
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        holder.getServlet().init(config);
    }

    @Override
    public void configure(WebSocketServletFactory factory) {
        final ApiMasterService svc = this.service;
        factory.setCreator(new WebSocketCreator() {
            @Override
            public Object createWebSocket(ServletUpgradeRequest servletUpgradeRequest, ServletUpgradeResponse servletUpgradeResponse) {
                WatchWebSocket answer = new WatchWebSocket(svc);
                return answer;
            }
        });
        //factory.register(WatchWebSocket.class);
    }

    @Override
    public void service(ServletRequest req, ServletResponse res) throws ServletException, IOException {
        HttpServletRequest request;
        HttpServletResponse response;
        try {
            request = (HttpServletRequest) req;
            response = (HttpServletResponse) res;
        } catch (ClassCastException e) {
            super.service(req, res);
            return;
        }
        if (request == null || response == null) {
            throw new ServletException("Request or response was null");
        }
        String connection = request.getHeader("Connection");
        if (connection != null && connection.equals("Upgrade")) {
            //LOG.debug("Got upgrade request: {}", req);
            super.service(req, res);
            //LOG.debug("Upgrade request response: {}", res);
        } else {
            //LOG.debug("Servicing normal request: {}", req);
            holder.getServlet().service(req, res);
            //LOG.debug("Normal request response: {}", res);
        }
    }
}
