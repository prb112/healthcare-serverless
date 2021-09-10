/*
 * (C) Copyright IBM Corp. 2021
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ibm.fhir.example;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.ibm.fhir.client.FHIRClient;
import com.ibm.fhir.client.FHIRClientFactory;
import com.ibm.fhir.core.FHIRMediaType;
import com.ibm.fhir.model.resource.Bundle;
import com.ibm.fhir.model.resource.Patient;
import com.ibm.fhir.model.resource.Resource;
import com.ibm.fhir.path.FHIRPathNode;
import com.ibm.fhir.path.evaluator.FHIRPathEvaluator;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;

/**
 * Client to connect to the IBM FHIR Server.
 */
public class Client {
    private static final Logger LOG = Logger.getLogger(Client.class.getName());
    public Client() {
        // No Operation
    }

    /**
     * converts the environment variables to Properties
     * @return
     * @throws Exception
     */
    public Properties generateProperties() throws Exception {
        Properties props = new Properties();
        props.load(this.getClass().getResourceAsStream("/endpoint.properties"));
        return props;
    }

    /**
     * submit bundle and generate a list of locations (e.g. resources with logical ids)
     * 
     * @param firstName
     * @param lastName
     * @return list of locations of the Resources created
     * @throws Exception
     */
    public List<String> submitBundle(String firstName, String lastName) throws Exception {
        Properties props = generateProperties();
        FHIRClient client = FHIRClientFactory.getClient(props);

        BundleGenerator generator = new BundleGenerator();
        Bundle bundle = generator.sampleData(firstName, lastName);

        Entity<Bundle> entity = Entity.entity(bundle, FHIRMediaType.APPLICATION_FHIR_JSON);
        Response response = client.getWebTarget().request().post(entity, Response.class);
        Bundle responseBundle = response.readEntity(Bundle.class);
        LOG.info("The Response is " + responseBundle);

        FHIRPathEvaluator evaluator = FHIRPathEvaluator.evaluator();
        Collection<FHIRPathNode> result = evaluator.evaluate(responseBundle, "entry.response.location");

        // Convert the Path Nodes to the List of Locations
        List<String> listOfLocations = new ArrayList<>();
        for (FHIRPathNode node : result) {
            String loc = node.asElementNode().element().as(com.ibm.fhir.model.type.Uri.class).getValue();
            listOfLocations.add(props.getProperty("fhirclient.rest.base.url") + "/" +  loc);
        }
        return listOfLocations;
    }

    /**
     * builds a report
     * 
     * https://localhost:9443/fhir-server/api/v4/MedicationAdministration
     *  ?_include=MedicationAdministration:patient&_include=MedicationAdministration:medication&_lastUpdated=2021-08-20
     * 
     * @return
     * @throws Exception
     */
    public String buildReport() throws Exception {
        FHIRClient client = FHIRClientFactory.getClient(generateProperties());
        
        ZonedDateTime zdt = ZonedDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        
        String ymd = formatter.format(zdt);

        Response response = client.getWebTarget()
                                    .path("/MedicationAdministration")
                                    .queryParam("_include", "MedicationAdministration:patient")
                                    .queryParam("_include", "MedicationAdministration:medication")
                                    .queryParam("_lastUpdated", ymd)
                                    .request(FHIRMediaType.APPLICATION_FHIR_JSON)
                                    .header("X-FHIR-TENANT-ID", "default")
                                    .header("X-FHIR-DSID", "default")
                                    .header("Content-Type", "application/fhir+json")
                                    .get(Response.class);

        Bundle responseBundle = response.readEntity(Bundle.class);
        LOG.info("The Report Response is " + responseBundle);

        StringBuilder builder = new StringBuilder();

        // Output the number of MedicationAdministrations
        builder.append("Total Number of Medication Administrations Today are: ")
                .append(responseBundle.getTotal().getValue());
        builder.append("\n");

        // Use FHIRPath to Extract the Entry Resource
        FHIRPathEvaluator evaluator = FHIRPathEvaluator.evaluator();
        Collection<FHIRPathNode> result = evaluator.evaluate(responseBundle, "Bundle.entry.resource");

        // Convert the Path Nodes to Patient Names
        int idx = 0;
        for (FHIRPathNode node : result) {
            Resource r = node.asResourceNode().resource();

            if (r instanceof Patient) {
                Patient patient = node.asResourceNode().resource().as(Patient.class);
                
                String patientName = patient.getName().stream()
                                        .map(name -> name.getGiven()
                                                            .stream()
                                                            .map(m -> m.getValue())
                                                            .collect(Collectors.joining(" "))
                                                    + " " 
                                                    + name.getFamily().getValue())
                                        .collect(Collectors.joining(","));
                builder.append("[").append(idx++).append("] ");
                builder.append(patientName);
                builder.append("\n");
            }
        }
        LOG.info("--> " + builder.toString());
        return builder.toString();
    }

}