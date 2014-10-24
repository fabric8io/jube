/**
 *  Copyright 2005-2014 Red Hat, Inc.
 *
 *  Red Hat licenses this file to you under the Apache License, version
 *  2.0 (the "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *  implied.  See the License for the specific language governing
 *  permissions and limitations under the License.
 */
package org.jboss.jube.main;

import org.apache.cxf.cdi.CXFCdiServlet;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.webapp.WebAppContext;
import org.jboss.weld.environment.servlet.BeanManagerResourceBindingListener;
import org.jboss.weld.environment.servlet.Listener;

import java.net.URL;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

public class Main {

    public static void main(final String[] args) throws Exception {
        try {
            System.setProperty("hawtio.authenticationEnabled", "false");
            String port = System.getenv("HTTP_PORT");
            if (port == null) {
                port = System.getProperty("http.port");
            }
            if (port == null) {
                port = "8585";
            }
            Integer portNumber = Integer.parseInt(port);

            System.out.println("Starting REST server on port: " + port);
            final Server server = new Server(portNumber);
            HandlerCollection handlers = new HandlerCollection();
            server.setHandler(handlers);

            // lets install the thread context class loader
            Set<String> foundURLs = new HashSet<>();
            findWarsOnClassPath(server, handlers, Thread.currentThread().getContextClassLoader(), foundURLs, portNumber);

            ClassLoader classLoader = Main.class.getClassLoader();
            Thread.currentThread().setContextClassLoader(classLoader);
            findWarsOnClassPath(server, handlers, classLoader, foundURLs, portNumber);
            if (foundURLs.isEmpty()) {
                System.out.println("WARNING: did not find any war files on the classpath to embed!");
            }

            // Register and map the dispatcher servlet
            final ServletHolder servletHolder = new ServletHolder(new CXFCdiServlet());

            // change default service list URI
            servletHolder.setInitParameter("service-list-path", "/cxf/servicesList");

            final ServletContextHandler context = new ServletContextHandler();
            context.setClassLoader(classLoader);
            context.setContextPath("/");
            context.addEventListener(new Listener());
            context.addEventListener(new BeanManagerResourceBindingListener());
            context.addServlet(servletHolder, "/api/*");

            handlers.addHandler(context);

            server.start();
            server.join();
        } catch (Exception e) {
            Set<Throwable> exceptions = new HashSet<>();
            exceptions.add(e);
            System.out.println(e);
            e.printStackTrace();

            // show all causes
            Throwable t = e;
            while (true) {
                Throwable cause = t.getCause();
                if (cause != null && exceptions.add(cause)) {
                    System.out.println();
                    System.out.println("Caused by: " + cause);
                    cause.printStackTrace();
                    t = cause;
                } else {
                    break;
                }
            }
        }
    }

    protected static void findWarsOnClassPath(Server server, HandlerCollection handlers, ClassLoader classLoader, Set<String> foundURLs, Integer port) {
        try {
            Enumeration<URL> resources = classLoader.getResources("WEB-INF/web.xml");
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                String text = url.toString();
                if (text.startsWith("jar:")) {
                    text = text.substring(4);
                }
                if (foundURLs.add(text)) {
                    String contextPath = createContextPath(text);
                    String filePath = createFilePath(text);
                    if (contextPath.equals("hawtio")) {
                        System.out.println();
                        System.out.println("==================================================");
                        System.out.println("hawtio is running on http://localhost:" + port + "/" + contextPath + "/");
                        System.out.println("==================================================");
                        System.out.println();
                    } else {
                        System.out.println("adding web context path: /" + contextPath + " war: " + filePath);
                    }
                    WebAppContext webapp = new WebAppContext();
                    webapp.setContextPath("/" + contextPath);
                    webapp.setWar("file://" + filePath);
                    handlers.addHandler(webapp);
                    try {
                        System.out.println("Starting web app: " + contextPath);
                        webapp.start();
                    } catch (Exception e) {
                        System.out.println("Failed: " + e);
                        e.printStackTrace();
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Failed to find web.xml on classpath: " + e);
            e.printStackTrace();
        }

    }

    /**
     * Returns the web context path for the given URL
     */
    public static String createContextPath(String uri) {
        String contextPath = trimUpToLastIndexOf(uri, '!', '.');
        contextPath = trimFromAfterLastIndexOf(contextPath, '/', '\\');
        if (contextPath.startsWith("kubernetes-war-")) {
            contextPath = "hawtio";
        }
        return contextPath;
    }


    /**
     * Returns the file path of the given URL
     */
    public static String createFilePath(String uri) {
        String answer = trimUpToLastIndexOf(uri, '!');
        int idx = answer.indexOf(':');
        if (idx > 0) {
            answer = answer.substring(idx + 1);
        }
        if (answer.startsWith("///")) {
            answer = answer.substring(2);
        }
        return answer;
    }

    public static String trimFromAfterLastIndexOf(String text, char... characters) {
        String answer = text;
        for (char ch : characters) {
            int idx = answer.lastIndexOf(ch);
            if (idx > 0) {
                answer = answer.substring(idx + 1, answer.length());
            }
        }
        return answer;
    }

    public static String trimUpToLastIndexOf(String text, char... characters) {
        String answer = text;
        for (char ch : characters) {
            int idx = answer.lastIndexOf(ch);
            if (idx > 0) {
                answer = answer.substring(0, idx);
            }
        }
        return answer;
    }

}
