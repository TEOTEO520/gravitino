/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.gravitino.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.gravitino.Catalog;
import org.apache.gravitino.CatalogChange;
import org.apache.gravitino.NameIdentifier;
import org.apache.gravitino.dto.AuditDTO;
import org.apache.gravitino.dto.CatalogDTO;
import org.apache.gravitino.dto.MetalakeDTO;
import org.apache.gravitino.dto.requests.CatalogCreateRequest;
import org.apache.gravitino.dto.requests.CatalogUpdateRequest;
import org.apache.gravitino.dto.requests.CatalogUpdatesRequest;
import org.apache.gravitino.dto.requests.MetalakeCreateRequest;
import org.apache.gravitino.dto.responses.CatalogListResponse;
import org.apache.gravitino.dto.responses.CatalogResponse;
import org.apache.gravitino.dto.responses.DropResponse;
import org.apache.gravitino.dto.responses.EntityListResponse;
import org.apache.gravitino.dto.responses.ErrorResponse;
import org.apache.gravitino.dto.responses.MetalakeResponse;
import org.apache.gravitino.exceptions.CatalogAlreadyExistsException;
import org.apache.gravitino.exceptions.NoSuchCatalogException;
import org.apache.gravitino.exceptions.NoSuchMetalakeException;
import org.apache.gravitino.exceptions.RESTException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Method;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class TestGravitinoMetalake extends TestBase {

  private static final String metalakeName = "test";

  private static final String provider = "test";

  protected static GravitinoClient gravitinoClient;

  @BeforeAll
  public static void setUp() throws Exception {
    TestBase.setUp();
    createMetalake(client, metalakeName);

    MetalakeDTO mockMetalake =
        MetalakeDTO.builder()
            .withName(metalakeName)
            .withComment("comment")
            .withAudit(
                AuditDTO.builder().withCreator("creator").withCreateTime(Instant.now()).build())
            .build();
    MetalakeResponse resp = new MetalakeResponse(mockMetalake);
    buildMockResource(Method.GET, "/api/metalakes/" + metalakeName, null, resp, HttpStatus.SC_OK);

    gravitinoClient =
        GravitinoClient.builder("http://127.0.0.1:" + mockServer.getLocalPort())
            .withMetalake(metalakeName)
            .withVersionCheckDisabled()
            .build();
  }

  @Test
  public void testListCatalogs() throws JsonProcessingException {
    String path = "/api/metalakes/" + metalakeName + "/catalogs";

    NameIdentifier ident1 = NameIdentifier.of(metalakeName, "mock");
    NameIdentifier ident2 = NameIdentifier.of(metalakeName, "mock2");

    EntityListResponse resp = new EntityListResponse(new NameIdentifier[] {ident1, ident2});
    buildMockResource(Method.GET, path, null, resp, HttpStatus.SC_OK);
    String[] catalogs = gravitinoClient.listCatalogs();

    Assertions.assertEquals(2, catalogs.length);
    Assertions.assertEquals(ident1.name(), catalogs[0]);
    Assertions.assertEquals(ident2.name(), catalogs[1]);

    // Test return empty catalog list
    EntityListResponse resp1 = new EntityListResponse(new NameIdentifier[] {});
    buildMockResource(Method.GET, path, null, resp1, HttpStatus.SC_OK);
    String[] catalogs1 = gravitinoClient.listCatalogs();
    Assertions.assertEquals(0, catalogs1.length);

    // Test return internal error
    ErrorResponse errorResp = ErrorResponse.internalError("mock error");
    buildMockResource(Method.GET, path, null, errorResp, HttpStatus.SC_INTERNAL_SERVER_ERROR);
    Throwable ex =
        Assertions.assertThrows(RuntimeException.class, () -> gravitinoClient.listCatalogs());
    Assertions.assertTrue(ex.getMessage().contains("mock error"));

    // Test return unparsed system error
    buildMockResource(Method.GET, path, null, "mock error", HttpStatus.SC_CONFLICT);
    Throwable ex1 =
        Assertions.assertThrows(RESTException.class, () -> gravitinoClient.listCatalogs());
    Assertions.assertTrue(ex1.getMessage().contains("Error code: " + HttpStatus.SC_CONFLICT));
  }

  @Test
  public void testListCatalogsInfo() throws JsonProcessingException {
    String path = "/api/metalakes/" + metalakeName + "/catalogs";
    Map<String, String> params = Collections.singletonMap("details", "true");

    CatalogDTO mockCatalog1 =
        CatalogDTO.builder()
            .withName("mock")
            .withComment("comment")
            .withType(Catalog.Type.RELATIONAL)
            .withProvider("test")
            .withAudit(
                AuditDTO.builder().withCreator("creator").withCreateTime(Instant.now()).build())
            .build();
    CatalogDTO mockCatalog2 =
        CatalogDTO.builder()
            .withName("mock2")
            .withComment("comment2")
            .withType(Catalog.Type.RELATIONAL)
            .withProvider("test")
            .withAudit(
                AuditDTO.builder().withCreator("creator").withCreateTime(Instant.now()).build())
            .build();
    CatalogListResponse resp =
        new CatalogListResponse(new CatalogDTO[] {mockCatalog1, mockCatalog2});
    buildMockResource(Method.GET, path, null, resp, HttpStatus.SC_OK);

    Catalog[] catalogs = gravitinoClient.listCatalogsInfo();
    Assertions.assertEquals(2, catalogs.length);
    Assertions.assertEquals("mock", catalogs[0].name());
    Assertions.assertEquals("comment", catalogs[0].comment());
    Assertions.assertEquals(Catalog.Type.RELATIONAL, catalogs[0].type());
    Assertions.assertEquals("mock2", catalogs[1].name());
    Assertions.assertEquals("comment2", catalogs[1].comment());
    Assertions.assertEquals(Catalog.Type.RELATIONAL, catalogs[1].type());

    // Test return no found
    ErrorResponse errorResponse =
        ErrorResponse.notFound(NoSuchMetalakeException.class.getSimpleName(), "mock error");
    buildMockResource(Method.GET, path, params, null, errorResponse, HttpStatus.SC_NOT_FOUND);
    Throwable ex =
        Assertions.assertThrows(
            NoSuchMetalakeException.class, () -> gravitinoClient.listCatalogsInfo());
    Assertions.assertTrue(ex.getMessage().contains("mock error"));
  }

  @Test
  public void testLoadCatalog() throws JsonProcessingException {
    String catalogName = "mock";
    String path = "/api/metalakes/" + metalakeName + "/catalogs/" + catalogName;

    CatalogDTO mockCatalog =
        CatalogDTO.builder()
            .withName("mock")
            .withComment("comment")
            .withType(Catalog.Type.RELATIONAL)
            .withProvider("test")
            .withAudit(
                AuditDTO.builder().withCreator("creator").withCreateTime(Instant.now()).build())
            .build();
    CatalogResponse resp = new CatalogResponse(mockCatalog);

    buildMockResource(Method.GET, path, null, resp, HttpStatus.SC_OK);
    Catalog catalog = gravitinoClient.loadCatalog(catalogName);

    Assertions.assertEquals(catalogName, catalog.name());
    Assertions.assertEquals("comment", catalog.comment());
    Assertions.assertEquals(Catalog.Type.RELATIONAL, catalog.type());

    // Test return not found
    ErrorResponse errorResponse =
        ErrorResponse.notFound(NoSuchCatalogException.class.getSimpleName(), "mock error");
    buildMockResource(Method.GET, path, null, errorResponse, HttpStatus.SC_NOT_FOUND);
    Throwable ex =
        Assertions.assertThrows(
            NoSuchCatalogException.class, () -> gravitinoClient.loadCatalog(catalogName));
    Assertions.assertTrue(ex.getMessage().contains("mock error"));

    // Test return unsupported catalog type
    CatalogDTO mockCatalog1 =
        CatalogDTO.builder()
            .withName("mock")
            .withComment("comment")
            .withType(Catalog.Type.UNSUPPORTED)
            .withProvider("test")
            .withAudit(
                AuditDTO.builder().withCreator("creator").withCreateTime(Instant.now()).build())
            .build();
    CatalogResponse resp1 = new CatalogResponse(mockCatalog1);
    buildMockResource(Method.GET, path, null, resp1, HttpStatus.SC_OK);
    Assertions.assertThrows(
        UnsupportedOperationException.class, () -> gravitinoClient.loadCatalog(catalogName));

    // Test return internal error
    ErrorResponse errorResp = ErrorResponse.internalError("mock error");
    buildMockResource(Method.GET, path, null, errorResp, HttpStatus.SC_INTERNAL_SERVER_ERROR);
    Throwable ex1 =
        Assertions.assertThrows(
            RuntimeException.class, () -> gravitinoClient.loadCatalog(catalogName));
    Assertions.assertTrue(ex1.getMessage().contains("mock error"));

    // Test return unparsed system error
    buildMockResource(Method.GET, path, null, "mock error", HttpStatus.SC_CONFLICT);
    Throwable ex2 =
        Assertions.assertThrows(
            RESTException.class, () -> gravitinoClient.loadCatalog(catalogName));
    Assertions.assertTrue(ex2.getMessage().contains("Error code: " + HttpStatus.SC_CONFLICT));
  }

  @Test
  public void testCreateCatalog() throws JsonProcessingException {
    String catalogName = "mock";
    String path = "/api/metalakes/" + metalakeName + "/catalogs";

    CatalogDTO mockCatalog =
        CatalogDTO.builder()
            .withName(catalogName)
            .withComment("comment")
            .withType(Catalog.Type.RELATIONAL)
            .withProvider("test")
            .withAudit(
                AuditDTO.builder().withCreator("creator").withCreateTime(Instant.now()).build())
            .build();
    CatalogCreateRequest req =
        new CatalogCreateRequest(
            catalogName, Catalog.Type.RELATIONAL, provider, "comment", Collections.emptyMap());
    CatalogResponse resp = new CatalogResponse(mockCatalog);
    buildMockResource(Method.POST, path, req, resp, HttpStatus.SC_OK);

    Catalog catalog =
        gravitinoClient.createCatalog(
            catalogName, Catalog.Type.RELATIONAL, provider, "comment", Collections.emptyMap());
    Assertions.assertEquals(catalogName, catalog.name());
    Assertions.assertEquals("comment", catalog.comment());
    Assertions.assertEquals(Catalog.Type.RELATIONAL, catalog.type());

    // Test return unsupported catalog type
    CatalogDTO mockCatalog1 =
        CatalogDTO.builder()
            .withName("mock")
            .withComment("comment")
            .withType(Catalog.Type.UNSUPPORTED)
            .withProvider("test")
            .withAudit(
                AuditDTO.builder().withCreator("creator").withCreateTime(Instant.now()).build())
            .build();
    CatalogCreateRequest req1 =
        new CatalogCreateRequest(
            catalogName, Catalog.Type.MESSAGING, provider, "comment", Collections.emptyMap());
    CatalogResponse resp1 = new CatalogResponse(mockCatalog1);
    buildMockResource(Method.POST, path, req1, resp1, HttpStatus.SC_OK);
    NameIdentifier id = NameIdentifier.of(metalakeName, catalogName);
    Map<String, String> emptyMap = Collections.emptyMap();

    Assertions.assertThrows(
        UnsupportedOperationException.class,
        () ->
            gravitinoClient.createCatalog(
                id.name(), Catalog.Type.MESSAGING, provider, "comment", emptyMap));

    // Test return NoSuchMetalakeException
    ErrorResponse errorResponse =
        ErrorResponse.notFound(NoSuchMetalakeException.class.getSimpleName(), "mock error");
    buildMockResource(Method.POST, path, req, errorResponse, HttpStatus.SC_NOT_FOUND);
    Throwable ex =
        Assertions.assertThrows(
            NoSuchMetalakeException.class,
            () ->
                gravitinoClient.createCatalog(
                    id.name(), Catalog.Type.RELATIONAL, provider, "comment", emptyMap));
    Assertions.assertTrue(ex.getMessage().contains("mock error"));

    // Test return CatalogAlreadyExistsException
    ErrorResponse errorResponse1 =
        ErrorResponse.alreadyExists(
            CatalogAlreadyExistsException.class.getSimpleName(), "mock error");
    buildMockResource(Method.POST, path, req, errorResponse1, HttpStatus.SC_CONFLICT);
    Throwable ex1 =
        Assertions.assertThrows(
            CatalogAlreadyExistsException.class,
            () ->
                gravitinoClient.createCatalog(
                    id.name(), Catalog.Type.RELATIONAL, provider, "comment", emptyMap));
    Assertions.assertTrue(ex1.getMessage().contains("mock error"));

    // Test return internal error
    ErrorResponse errorResp = ErrorResponse.internalError("mock error");
    buildMockResource(Method.POST, path, req, errorResp, HttpStatus.SC_INTERNAL_SERVER_ERROR);
    Throwable ex2 =
        Assertions.assertThrows(
            RuntimeException.class,
            () ->
                gravitinoClient.createCatalog(
                    id.name(), Catalog.Type.RELATIONAL, provider, "comment", emptyMap));
    Assertions.assertTrue(ex2.getMessage().contains("mock error"));
  }

  @Test
  public void testAlterCatalog() throws JsonProcessingException {
    String catalogName = "mock";
    String path = "/api/metalakes/" + metalakeName + "/catalogs/" + catalogName;

    CatalogDTO mockCatalog =
        CatalogDTO.builder()
            .withName("mock1")
            .withComment("comment1")
            .withType(Catalog.Type.RELATIONAL)
            .withProvider("test")
            .withAudit(
                AuditDTO.builder().withCreator("creator").withCreateTime(Instant.now()).build())
            .build();
    CatalogResponse resp = new CatalogResponse(mockCatalog);

    CatalogChange change1 = CatalogChange.rename("mock1");
    CatalogChange change2 = CatalogChange.updateComment("comment1");
    List<CatalogUpdateRequest> reqs =
        Arrays.asList(change1, change2).stream()
            .map(DTOConverters::toCatalogUpdateRequest)
            .collect(Collectors.toList());
    CatalogUpdatesRequest updatesRequest = new CatalogUpdatesRequest(reqs);

    buildMockResource(Method.PUT, path, updatesRequest, resp, HttpStatus.SC_OK);
    Catalog catalog = gravitinoClient.alterCatalog(catalogName, change1, change2);
    Assertions.assertEquals("mock1", catalog.name());
    Assertions.assertEquals("comment1", catalog.comment());
    Assertions.assertEquals(Catalog.Type.RELATIONAL, catalog.type());

    // Test return NoSuchCatalogException
    ErrorResponse errorResponse =
        ErrorResponse.notFound(NoSuchCatalogException.class.getSimpleName(), "mock error");
    buildMockResource(Method.PUT, path, updatesRequest, errorResponse, HttpStatus.SC_NOT_FOUND);
    Throwable ex =
        Assertions.assertThrows(
            NoSuchCatalogException.class,
            () -> gravitinoClient.alterCatalog(catalogName, change1, change2));
    Assertions.assertTrue(ex.getMessage().contains("mock error"));

    // Test return IllegalArgumentException
    ErrorResponse errorResponse1 = ErrorResponse.illegalArguments("mock error");
    buildMockResource(Method.PUT, path, updatesRequest, errorResponse1, HttpStatus.SC_BAD_REQUEST);
    Throwable ex1 =
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> gravitinoClient.alterCatalog(catalogName, change1, change2));
    Assertions.assertTrue(ex1.getMessage().contains("mock error"));

    // Test return internal error
    ErrorResponse errorResp = ErrorResponse.internalError("mock error");
    buildMockResource(
        Method.PUT, path, updatesRequest, errorResp, HttpStatus.SC_INTERNAL_SERVER_ERROR);
    Throwable ex2 =
        Assertions.assertThrows(
            RuntimeException.class,
            () -> gravitinoClient.alterCatalog(catalogName, change1, change2));
    Assertions.assertTrue(ex2.getMessage().contains("mock error"));
  }

  @Test
  public void testDropCatalog() throws JsonProcessingException {
    String catalogName = "mock";
    String path = "/api/metalakes/" + metalakeName + "/catalogs/" + catalogName;

    DropResponse resp = new DropResponse(true);
    buildMockResource(Method.DELETE, path, null, resp, HttpStatus.SC_OK);
    boolean dropped = gravitinoClient.dropCatalog(catalogName);
    Assertions.assertTrue(dropped, "catalog should be dropped");

    // Test return false
    DropResponse resp1 = new DropResponse(false);
    buildMockResource(Method.DELETE, path, null, resp1, HttpStatus.SC_OK);
    boolean dropped1 = gravitinoClient.dropCatalog(catalogName);
    Assertions.assertFalse(dropped1, "catalog should be non-existent");
  }

  static GravitinoMetalake createMetalake(GravitinoAdminClient client, String metalakeName)
      throws JsonProcessingException {
    MetalakeDTO mockMetalake =
        MetalakeDTO.builder()
            .withName(metalakeName)
            .withComment("comment")
            .withAudit(
                AuditDTO.builder().withCreator("creator").withCreateTime(Instant.now()).build())
            .build();
    MetalakeCreateRequest req =
        new MetalakeCreateRequest(metalakeName, "comment", Collections.emptyMap());
    MetalakeResponse resp = new MetalakeResponse(mockMetalake);
    buildMockResource(Method.POST, "/api/metalakes", req, resp, HttpStatus.SC_OK);

    return client.createMetalake(metalakeName, "comment", Collections.emptyMap());
  }
}
