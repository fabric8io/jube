package io.fabric8.jube.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.jube.apimaster.ApiMasterKubernetesModel;
import io.fabric8.jube.apimaster.ApiMasterService;
import io.fabric8.jube.local.EntityListener;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.*;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.UpgradeRequest;
import org.eclipse.jetty.websocket.api.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@WebSocket
public class WatchWebSocket {

    private static final transient Logger LOG = LoggerFactory.getLogger(WatchWebSocket.class);
    private Session session;
    private ApiMasterService service;
    private ApiMasterKubernetesModel model;
    private ObjectMapper mapper;
    private EntityListener listener;
    private RemoveListener removeListenerFunction;

    private interface RemoveListener {
        void doRemove();
    }

    public WatchWebSocket(ApiMasterService svc) {
        this.service = svc;
        this.model = svc.getModel();
        this.mapper = new ObjectMapper();
    }

    private String parseNamespace(String path) {
        String[] parts = path.split("/");
        List<String> partsList = Arrays.asList(parts);
        if (partsList.contains("namespaces")) {
            int index = partsList.indexOf("namespaces");
            if (index == partsList.size()) {
                return null;
            }
            return partsList.get(index + 1);
        }
        return null;
    }

    private String parseType(String path) {
        String[] parts = path.split("/");
        if (parts.length == 0) {
            LOG.debug("Bogus path supplied: ", path);
            return null;
        }
        String answer = parts[parts.length - 1];
        LOG.debug("Type for {} is {}", path, answer);
        return answer;
    }

    private void sendAddedMessages(List objects) {
        for (Object obj : objects) {
            Map<String, Object> msg = getAddedMessage(obj);
            sendAsJson(msg);
        }
    }

    // called when the socket connection with the browser is established
    @OnWebSocketConnect
    public void handleConnect(Session session) {
        //LOG.debug("Client connected!  Session: {}", session);
        //LOG.debug("Request: {}", session.getUpgradeRequest());
        ValidateUpgradeRequest validateUpgradeRequest = new ValidateUpgradeRequest(session).invoke();
        if (validateUpgradeRequest.is()) return;
        String path = validateUpgradeRequest.getPath();
        this.session = session;
        String type = parseType(path);
        if (type == null) {
            session.close(0, "Invalid type specified");
            return;
        }
        if (type.equals("namespaces")) {
            NamespaceList namespaces = this.service.getNamespaces();
            sendAddedMessages(namespaces.getItems());
        } else {
            String namespace = parseNamespace(path);
            if (namespace == null) {
                session.close(0, "No namespace specified");
                return;
            }
            Object objs = null;
            switch(type) {
                case "pods":
                    objs = this.service.getPods(namespace);
                    this.listener = new EntityListener<Pod>() {
                        @Override
                        public void entityChanged(String id, Pod entity) {
                            Map<String, Object> msg = getModifiedMessage(entity);
                            sendAsJson(msg);
                        }
                        @Override
                        public void entityDeleted(String id) {
                            Pod entity = new Pod();
                            entity.setMetadata(new ObjectMeta());
                            KubernetesHelper.setName(entity, id);
                            Map<String, Object> msg = getDeletedMessage(entity);
                            sendAsJson(msg);
                        }
                    };
                    this.model.addPodListener(this.listener);
                    this.removeListenerFunction = new RemoveListener() {
                        @Override
                        public void doRemove() {
                            model.removePodListener(listener);
                        }
                    };
                    break;
                case "replicationcontrollers":
                    objs = this.service.getReplicationControllers(namespace);
                    this.listener = new EntityListener<ReplicationController>() {
                        @Override
                        public void entityChanged(String id, ReplicationController entity) {
                            Map<String, Object> msg = getModifiedMessage(entity);
                            sendAsJson(msg);
                        }
                        @Override
                        public void entityDeleted(String id) {
                            ReplicationController entity = new ReplicationController();
                            entity.setMetadata(new ObjectMeta());
                            KubernetesHelper.setName(entity, id);
                            Map<String, Object> msg = getDeletedMessage(entity);
                            sendAsJson(msg);
                        }
                    };
                    this.model.addReplicationControllerListener(this.listener);
                    this.removeListenerFunction = new RemoveListener() {
                        @Override
                        public void doRemove() {
                            model.removeReplicationControllerListener(listener);
                        }
                    };
                    break;
                case "services":
                    objs = this.service.getServices(namespace);
                    this.listener = new EntityListener<Service>() {
                        @Override
                        public void entityChanged(String id, Service entity) {
                            Map<String, Object> msg = getModifiedMessage(entity);
                            sendAsJson(msg);
                        }
                        @Override
                        public void entityDeleted(String id) {
                            Service entity = new Service();
                            entity.setMetadata(new ObjectMeta());
                            KubernetesHelper.setName(entity, id);
                            Map<String, Object> msg = getDeletedMessage(entity);
                            sendAsJson(msg);
                        }
                    };
                    this.model.addServiceListener(this.listener);
                    this.removeListenerFunction = new RemoveListener() {
                        @Override
                        public void doRemove() {
                            model.removeServiceListener(listener);
                        }
                    };
                    break;
                case "endpoints":
                    objs = this.service.getEndpoints(namespace);
                    // TODO add listener
                    break;
                default:
                    session.close(0, "Unsupported object type: " + type);
                    return;
            }
            if (objs == null) {
                session.close(0, "Failed to gather up objects of type: " + type);
                return;
            }
            try {
                Method method = objs.getClass().getMethod("getItems", null);
                sendAddedMessages((List)method.invoke(objs, null));
            } catch (NoSuchMethodException e) {
                LOG.debug("Failed to send messages due to: ", e);
            } catch (InvocationTargetException e) {
                LOG.debug("Failed to send messages due to: ", e);
            } catch (IllegalAccessException e) {
                LOG.debug("Failed to send messages due to: ", e);
            }
        }
    }

    private Map<String, Object> getMessage(Object obj, String type) {
        Map<String, Object> msg = new HashMap<String, Object>();
        msg.put("type", type);
        msg.put("object", obj);
        return msg;
    }

    private Map<String, Object> getDeletedMessage(Object obj) {
        return getMessage(obj, "DELETED");
    }

    private Map<String, Object> getModifiedMessage(Object obj) {
        return getMessage(obj, "MODIFIED");
    }

    private Map<String, Object> getAddedMessage(Object obj) {
        return getMessage(obj, "ADDED");
    }

    private void sendAsJson(Object msg) {
        String msgStr = null;
        try {
            msgStr = mapper.writeValueAsString(msg);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        if (msgStr != null) {
            LOG.debug("Sending message: {}", msgStr);
            send(msgStr);
        }
    }

    // called when the connection closed
    @OnWebSocketClose
    public void handleClose(int statusCode, String reason) {
        if (removeListenerFunction != null) {
            removeListenerFunction.doRemove();
        }
        LOG.debug("Connection closed with statusCode="
                + statusCode + ", reason=" + reason);
    }

    // called when a message received from the browser
    @OnWebSocketMessage
    public void handleMessage(String message) {
        LOG.debug("Got a message! - " + message);
    }

    // called in case of an error
    @OnWebSocketError
    public void handleError(Throwable error) {
        error.printStackTrace();
    }

    // sends message to browser
    private void send(String message) {
        try {
            if (session.isOpen()) {
                session.getRemote().sendString(message);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // closes the socket
    private void stop() {
        try {
            session.disconnect();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private class ValidateUpgradeRequest {
        private boolean myResult;
        private Session session;
        private String path;

        public ValidateUpgradeRequest(Session session) {
            this.session = session;
        }

        boolean is() {
            return myResult;
        }

        public String getPath() {
            return path;
        }

        public ValidateUpgradeRequest invoke() {
            UpgradeRequest request = session.getUpgradeRequest();
            URI uri = request.getRequestURI();
            path = uri.getPath();
            Map<String, List<String>> params = request.getParameterMap();
            List<String> watch = params.get("watch");
            LOG.debug("path: {}", path);
            if (watch == null || watch.size() != 1) {
                session.close(0, " must specify watch parameter when connecting via websocket");
                myResult = true;
                return this;
            }
            if (!watch.get(0).equalsIgnoreCase("true")) {
                session.close(0, " must specify watch parameter equal to 'true' when connecting via websocket");
                myResult = true;
                return this;
            }
            myResult = false;
            return this;
        }
    }
}
