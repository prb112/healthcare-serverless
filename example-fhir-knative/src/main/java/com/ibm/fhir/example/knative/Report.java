/*
 * (C) Copyright IBM Corp. 2021
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ibm.fhir.example.knative;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import com.ibm.fhir.example.Client;

/**
 * Wraps the Call to the IBM FHIR Server to report on current Status
 */
@Path("/v1/api/report")
public class Report {

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String generateReport() throws Exception {
        Client client = new Client();
        return client.buildReport();
    }
}