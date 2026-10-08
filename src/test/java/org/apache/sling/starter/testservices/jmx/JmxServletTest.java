/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sling.starter.testservices.jmx;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.StandardMBean;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonReader;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class JmxServletTest {

    public interface TestMBean {}

    public static class TestImpl implements TestMBean {}

    private MBeanServer server;
    private List<ObjectName> registeredNames;
    private JmxServlet servlet;

    @Before
    public void setUp() {
        server = ManagementFactory.getPlatformMBeanServer();
        registeredNames = new ArrayList<>();
        servlet = new JmxServlet();
    }

    @After
    public void tearDown() throws Exception {
        for (ObjectName name : registeredNames) {
            if (server.isRegistered(name)) {
                server.unregisterMBean(name);
            }
        }
    }

    private void registerTestMBean(String nameStr) throws Exception {
        ObjectName name = new ObjectName(nameStr);
        server.registerMBean(new StandardMBean(new TestImpl(), TestMBean.class), name);
        registeredNames.add(name);
    }

    @Test
    public void testDoGetWithSpecialMBeanNames() throws Exception {
        // 1. Plain name
        registerTestMBean("org.apache.sling:type=PlainName");
        // 2. Quoted property value containing quotes and backslashes
        registerTestMBean("org.apache.sling:type=\"Quoted\\\"With\\\\Backslash\"");
        // 3. Unquoted property value containing backslashes
        registerTestMBean("org.apache.sling:type=Unquoted\\With\\Backslash");

        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        String[] contentType = new String[1];
        int[] status = new int[1];

        SlingHttpServletRequest request = createStubRequest();
        SlingHttpServletResponse response = createStubResponse(printWriter, contentType, status);

        servlet.doGet(request, response);

        assertEquals("application/json", contentType[0]);
        assertEquals(200, status[0]);

        printWriter.flush();
        String jsonOutput = stringWriter.toString();

        // Verify that jsonOutput parses correctly as a JSON array
        try (JsonReader reader =
                Json.createReader(new ByteArrayInputStream(jsonOutput.getBytes(StandardCharsets.UTF_8)))) {
            JsonArray jsonArray = reader.readArray();
            List<String> values = new ArrayList<>();
            for (int i = 0; i < jsonArray.size(); i++) {
                values.add(jsonArray.getString(i));
            }

            for (ObjectName name : registeredNames) {
                assertTrue("JSON output missing MBean name: " + name.toString(), values.contains(name.toString()));
            }
        }
    }

    private SlingHttpServletRequest createStubRequest() {
        return (SlingHttpServletRequest) Proxy.newProxyInstance(
                SlingHttpServletRequest.class.getClassLoader(),
                new Class<?>[] {SlingHttpServletRequest.class},
                (proxy, method, args) -> null);
    }

    private SlingHttpServletResponse createStubResponse(
            PrintWriter printWriter, String[] contentTypeHolder, int[] statusHolder) {
        return (SlingHttpServletResponse) Proxy.newProxyInstance(
                SlingHttpServletResponse.class.getClassLoader(),
                new Class<?>[] {SlingHttpServletResponse.class},
                (proxy, method, args) -> {
                    if ("getWriter".equals(method.getName())) {
                        return printWriter;
                    } else if ("setContentType".equals(method.getName())) {
                        contentTypeHolder[0] = (String) args[0];
                        return null;
                    } else if ("setStatus".equals(method.getName())) {
                        statusHolder[0] = (Integer) args[0];
                        return null;
                    }
                    return null;
                });
    }
}
