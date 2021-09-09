/*
 * (C) Copyright IBM Corp. 2021
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ibm.fhir.example.knative;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;

@QuarkusTest
public class ResourceTest {

    // The test is commented out as it will not run without an IBM FHIR Server behind it.
    @Test
    public void testLoaderEndpoint() {
        given()
          .when()
            .queryParam("first", "Fred")
            .queryParam("last", "Doe")
            .get("/v1/api/loader")
          .then()
             .statusCode(200)
             .body(containsString("Patient/"));
    }

    // The test is commented out as it will not run without an IBM FHIR Server behind it.
    // @Test
    public void testReport() {
        given()
          .when()
            .get("/v1/api/report")
          .then()
             .statusCode(200)
             .body(containsString("Total Number of Medication Administrations Today are"));
    }
}