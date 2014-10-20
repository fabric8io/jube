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
package io.jimagezip.main;

import org.apache.cxf.cdi.CXFCdiServlet;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.jboss.weld.environment.servlet.BeanManagerResourceBindingListener;
import org.jboss.weld.environment.servlet.Listener;

public class Main {

    public static void main(final String[] args) throws Exception {
        System.setProperty("java.protocol.handler.pkgs", "sun.net.www.protocol");

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
        final ServletContextHandler context = new ServletContextHandler();
        context.setClassLoader(classLoader);
        context.setContextPath("/");
        context.addEventListener(new Listener());
        context.addEventListener(new BeanManagerResourceBindingListener());
        context.addServlet(servletHolder, "/*");

        server.setHandler(context);
        server.start();
        server.join();
    }

}
