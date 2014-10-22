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
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.jboss.weld.environment.servlet.BeanManagerResourceBindingListener;
import org.jboss.weld.environment.servlet.Listener;

import java.util.HashSet;
import java.util.Set;

public class Main {

    public static void main(final String[] args) throws Exception {
        try {
            String port = System.getenv("HTTP_PORT");
            if (port == null) {
                port = System.getProperty("http.port");
            }
            if (port == null) {
                port = "8585";
            }
            Integer num = Integer.parseInt(port);

            // lets install the thread context class loader
            ClassLoader classLoader = Main.class.getClassLoader();
            Thread.currentThread().setContextClassLoader(classLoader);

            System.out.println("Starting REST server on port: " + port);
            final Server server = new Server(num);

            // Register and map the dispatcher servlet
            final ServletHolder servletHolder = new ServletHolder(new CXFCdiServlet());

            // change default service list URI
            servletHolder.setInitParameter("service-list-path", "/cxf/servicesList");

            final ServletContextHandler context = new ServletContextHandler();
            context.setClassLoader(classLoader);
            context.setContextPath("/");
            context.addEventListener(new Listener());
            context.addEventListener(new BeanManagerResourceBindingListener());
            context.addServlet(servletHolder, "/*");

            server.setHandler(context);
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

}
