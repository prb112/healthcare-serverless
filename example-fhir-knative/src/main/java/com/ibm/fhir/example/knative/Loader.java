/*
 * (C) Copyright IBM Corp. 2021
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ibm.fhir.example.knative;

import java.util.Arrays;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import com.ibm.fhir.example.Client;

import org.jboss.resteasy.annotations.jaxrs.QueryParam;

/**
 * Wraps the Call to the IBM FHIR Server to Load the Bundle with the given and family name.
 */
@Path("/v1/api/loader")
public class Loader {

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public List<String> loadPatient(
            @QueryParam(value="first") String firstName,
            @QueryParam(value="last") String lastName) throws Exception {

        try { 
            Client client = new Client();
            return client.submitBundle(firstName, lastName);
        } catch (Exception e) { 
            return Arrays.asList(e.toString() + "/" + e);
        }
    }
}