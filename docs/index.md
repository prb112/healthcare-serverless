# Integrating Healthcare Data in a Serverless World

Welcome to the Digital Developer Conference Hybrid Cloud - Integrating Healthcare Data in a Serverless World! 

In this lab, developers integrate a healthcare data application using [IBM FHIR Server](https://ibm.github.io/FHIR/) with [Red Hat OpenShift Serverless](https://cloud.redhat.com/learn/topics/serverless) to create and respond to a healthcare scenario.

This lab is a companion to the session *Integrating Healthcare Data in a Serverless World* at [Digital Developer Conference - Hybrid Cloud](https://developer.ibm.com/conferences/digital-developer-conference-hybrid-cloud/).

The content for this lab can be found at [https://ibm.biz/ibm-fhir-server-healthcare-serverless](https://ibm.biz/ibm-fhir-server-healthcare-serverless).

Have fun!

## To Get Started Right Away with the conference...
1. Get added to the “DEGCLOUD” IBM Cloud account using the following app - https://account-invite.mybluemix.net/
- lab key: welcome
- IBM ID: the email you used to create your IBM Cloud account
2. You will then get an invite message in your email which you must accept. It doesn’t matter if you see an “oops” message here. You must accept the invite before continuing.
3. Then, you can use the following app to get access to a cluster. - https://ddc-healthcare-lab.mybluemix.net
- Lab key: oslab
- IBM ID: The email you used to create your IBM Cloud account

## **Introduction**

For each lab participant, the lab has two distinct sections - Project and Test. 

Each attendee should complete the Prerequisites section.

#### **Project**
1. Connect with the IBM Cloud CLI and OpenShift CLI
2. Clone the repo
3. Download and Load the Project
4. Review the Serverless project
5. Build the Serverless project
6. Push to the OpenShift Container Registry
7. Create the OpenShift Serverless service

#### **Test**

{:start="8"}
8. Test the Serverless Function to load data
9. Confirm Your data is loaded using FHIR Search
10. Test the Serverless Function to search and read data

## **Prerequisites**

The lab proctors have previously completed the <a href="#setup"><b>Appendix: Setup</b></a>. If you are running outside of the Lab Environment, prior to completing the Project and Test sections, please complete the <a href="#setup"><b>Appendix: Setup</b></a>.

1. Install Docker [docker](https://docs.docker.com/engine/install/)

2. Log in, or create an account on [IBM Cloud](https://cloud.ibm.com)

3. Install the Red Hat OpenShift cli `oc` [Link  to the Install](https://cloud.ibm.com/docs/openshift?topic=openshift-openshift-cli)

4. Install the Knative cli `kn` [Link to the Install](https://knative.dev/docs/client/install-kn/)

5. *Optional*: Install Maven [mvn](https://maven.apache.org/download.cgi) and Java.  You should use Java 11 for this lab. You can download from the [AdoptOpenJDK website](https://adoptopenjdk.net/installation.html#)

# *Project*

### 1. Connect with the IBM Cloud CLI and OpenShift CLI

1. Log in to [IBM Cloud](https://cloud.ibm.com/kubernetes/clusters)

1. Click on your cluster

1. Click on OpenShift Web Console

1. Click on the User dropdown in the Upper Right > Copy Login Command

1. A new tab opens, Click Display Token and Copy the Contents

1. On a terminal window, paste the contents on the terminal.

    ~~~ sh
    oc login --token=sha256~*** \
        --server=https://***.us-east.containers.cloud.ibm.com:32689
    ~~~

    You see: 

    ~~~ sh
    Logged into "https://****.us-east.containers.cloud.ibm.com:32689" as "IAM#***" using the token provided.

    You have access to 69 projects, the list has been suppressed. You can list all projects with 'oc projects'

    Using project "default".
    ~~~

### 2. Clone the repo

1. Clone the repository 

    ```
    git clone https://github.com/prb112/healthcare-serverless.git
    ```

    You see the repository is cloned and unpacked.

    ```
    Cloning into 'healthcare-serverless'...
    remote: Enumerating objects: 10, done.
    remote: Counting objects: 100% (10/10), done.
    remote: Compressing objects: 100% (7/7), done.
    remote: Total 10 (delta 1), reused 6 (delta 1), pack-reused 0
    Unpacking objects: 100% (10/10), done.
    ```

1. Change to the healthcare-serverless repository

    ~~~ sh
    cd healthcare-serverless
    ~~~

### 3. Download and Load the Project

1. Open with your preferred Integrated Development Environment (IDE), such as [Visual Code](https://code.visualstudio.com/).

1. Change directory to the `example-fhir-knative` project

    ```
    cd example-fhir-knative
    ```

Once you have the project in the IDE, you are ready to review and build.

### 4. Review the Serverless Project

1. Review the `src/main/resources/endpoint.properties` a simple properties file. Notice the property points to the IBM FHIR Server - `fhirclient.rest.base.url`. If you are using this pattern in production, you can load all these values from a secret.

1. Review the API, and you'll see how the API takes Query Parameters first and last name, and runs the Client.

    - `src/main/java/com/ibm/fhir/example/knative/Loader.java` - The Loader API takes two simple HTTP Query Parameters, a first and last name, and submits a FHIR Bundle for the User identified by firstName and lastName.
        
        ``` java
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
        ```

    - `src/main/java/com/ibm/fhir/example/knative/Report.java` - The Report API calls the Client API.

        ``` java
        @Path("/v1/api/report")
        public class Report {

            @GET
            @Produces(MediaType.TEXT_PLAIN)
            public String generateReport() throws Exception {
                Client client = new Client();
                return client.buildReport();
            }
        }
        ```

1. Review the `/src/main/java/com/ibm/fhir/example/Client.java` and you'll find the dependency `fhir-client` is used to call the backend. The backend configuration is loaded from the endpoint.properties, and facilitates the creation and retrieval of FHIR data.

    The submitBundle creates a FHIRClient loads an endpoint configuration, and submits a generated bundle to the backend and generates a list of Resources extracted using the `fhir-path` module.

    ``` java
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
    ```

    The buildReport also uses the FHIRClient to call the backend using Search Parameters (HTTP Query parameters) and returns a FHIR Bundle in the `application/fhir+json` format. The content is extracted, and the total number of Patients with [MedicationAdministration](https://www.hl7.org/fhir/medicationadministration.html) resources today are returned. From this basic pattern you can execute even more complicated healtcare queries.

    ``` java
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
                                                    + "," 
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
    ```

1. Review the `/src/main/java/com/ibm/fhir/example/BundleGenerator.java` and you'll see the `fhir-model` is used to build a set of resources in the HL7 FHIR Standard.

    The sampleData method uses the IBM FHIR Server's `fhir-model` to create a [Bundle](https://www.hl7.org/fhir/Bundle.html) that reflects a conformant set of Resources for the patient.  This particular patient has a Medication prescribed for his observed (Observation) Blood Pressure and subsequently administered the medicine (MedicationAdministration).
   
    With the following method, you see how the HL7 FHIR model is wrapped in a set of builders to create an Observation of the Patient's Blood Pressure reading. A very cool thing here is the `.subject(Reference.builder().reference(string("Patient/" + patientId)).build())` which is a relative reference to the Patient. Relative references allow us to retrieve the Patient's resources in one call.

    ``` java
    public Observation buildObservation(String patientId) {
        CodeableConcept code = CodeableConcept.builder()
            .coding(
                Coding.builder()
                    .code(Code.of("85354-9"))
                    .system(Uri.of("http://loinc.org"))
                    .display(string("Blood pressure panel with all children optional"))
                    .build())
            .text(string("Blood pressure systolic & diastolic"))
            .build();

        Observation observation = Observation.builder()
                .status(ObservationStatus.FINAL)
                .category(
                    CodeableConcept.builder()
                        .coding(
                            Coding.builder()
                                .system(Uri.of("http://terminology.hl7.org/CodeSystem/observation-category"))
                                .code(Code.of("vital-signs"))
                                .display(string("Vital Signs"))
                            .build())
                            .text(string("Vital Signs"))
                        .build())
                .bodySite(
                    CodeableConcept.builder()
                        .coding(Coding.builder().code(Code.of("55284-4"))
                        .system(Uri.of("http://loinc.org")).build())
                        .text(string("Blood pressure systolic & diastolic")).build())
                .code(code)
                .subject(Reference.builder().reference(string("Patient/" + patientId)).build())
                .component(Component.builder().code(CodeableConcept.builder().coding(Coding.builder().code(Code.of("8480-6"))
                        .system(Uri.of("http://loinc.org")).build())
                        .text(string("Systolic blood pressure")).build())
                        .value(Quantity.builder().value(Decimal.of(124.9)).unit(string("mmHg")).build()).build())
                .component(Component.builder().code(CodeableConcept.builder().coding(Coding.builder().code(Code.of("8462-4"))
                        .system(Uri.of("http://loinc.org")).build())
                        .text(string("Diastolic blood pressure")).build())
                        .value(Quantity.builder().value(Decimal.of(93.7)).unit(string("mmHg")).build()).build())
                .text(
                    Narrative.builder()
                        .div(Xhtml.of("<div xmlns=\"http://www.w3.org/1999/xhtml\">GENERATED</div>"))
                        .status(NarrativeStatus.GENERATED).build())
                .build();
        return observation;
    }
    ```

### 5. Build the Serverless project

1. Build the project resources

1. If you have Maven installed: 

    ```
    mvn clean install
    ```

    You should see `BUILD SUCCESS`. Note, if you do not, you 

    ```
    [INFO] -------------------------------------------------
    [INFO] BUILD SUCCESS
    [INFO] -------------------------------------------------
    [INFO] Total time:  9.077 s
    [INFO] Finished at: 2021-09-07T16:02:16-04:00
    [INFO] -------------------------------------------------
    ```

1. If you do not have Maven installed

    1. Extract the example-fhir-knative-target.zip file.

    ```
    unzip -qu example-fhir-knative-target.zip
    ```

    1. Check the files exist

    ```
    ls -1 target
    ```

    You'll see: 

    ```
    classes
    fhir-knative-99-SNAPSHOT.jar
    generated-sources
    generated-test-sources
    maven-archiver
    maven-status
    quarkus-app
    quarkus-artifact.properties
    test-classes
    ```

1. Build the Docker image

    ```
    docker build -qf src/main/docker/Dockerfile \
        -t example/fhir-knative-jvm:latest .
    ```

    You'll see the image built, and named.

    ```
    sha256:46991973ed52828793cfbab5f16cdd1608576e1f29d9183c6e5d04af9ee41e83
    ```

### 6. Push to the OpenShift Container Registry

1. Change to your project, such as `my-fhir-project`. If you need to create a project, you can first run `oc new-project my-fhir-project` to create a project.

    ```
    oc project my-fhir-project
    ```

    You have changed your name space

    ```
    Now using project "my-fhir-project" on server "https://host.us-east.containers.cloud.ibm.com:9999".
    ```

1. Check the OpenShift Image registry host

    ```
    oc get route default-route -n openshift-image-registry \
         --template='{{ .spec.host }}'
    ```
    
    You see the hostname printed. 

    ```
    default-route-openshift-image-registry.healthcare-serverless-9999-0000.us-east.containers.appdomain.cloud
    ```

1. Create an image stream

    ```
    oc create imagestream fhir-knative-jvm
    ```

    You see a new image stream is created.

    ```
    imagestream.image.openshift.io/fhir-knative-jvm created
    ```

1. Tag the Image for the Image Registry

    ```
    docker tag example/fhir-knative-jvm:latest \
        $(oc get route default-route -n openshift-image-registry --template='{{ .spec.host }}')/$(oc project --short=true)/fhir-knative-jvm:latest
    ```

1. Login to the Image Registry, and confirm you see `Login Succeeded`.

    ```
    docker login -u `oc whoami` -p `oc whoami --show-token` \
        $(oc get route default-route -n openshift-image-registry --template='{{ .spec.host }}')
    ```

1. Push the Image into the OpenShift Image Registry

    ```
    docker push \
        $(oc get route default-route -n openshift-image-registry --template='{{ .spec.host }}')/$(oc project --short=true)/fhir-knative-jvm:latest
    ```

    You see the image is on the server

    ```
    The push refers to repository [default-route-openshift-image-registry.0000.us-east.containers.appdomain.cloud/my-fhir-project/fhir-knative-jvm]
    5f70bf18a086: Pushed 
    d23bc788009e: Pushed 
    8cf05ebe3167: Pushed 
    e7ed17121dee: Mounted from fhir-serverless/fhir-knative-jvm 
    785573c4b945: Mounted from fhir-serverless/fhir-knative-jvm 
    latest: digest: sha256:27e52ab6a53cfc2d74350b230eb0c8693af0875c63c66f117f386cadb08fbf44 size: 1357
    ```

    If you see `unauthorized: authentication required`, please be sure to `docker login` in the prior step.

    If you see `Error from server (NotFound): routes.route.openshift.io "default-route" not found`, be sure to expose your [OpenShift Registry with an External Route](https://docs.openshift.com/container-platform/4.7/registry/securing-exposing-registry.html).

1. Make the local registry lookup use relative names

    ```
    oc set image-lookup  --all
    ```

    You see, and now you can use relative names to find your image.

    ```
    imagestream.image.openshift.io/fhir-knative-jvm image lookup updated
    ```

### 7. Create the OpenShift Serverless service

1. Create the Knative service

    ```
    kn service create myfhir --force \
        --image image-registry.openshift-image-registry.svc:5000/$(oc project --short=true)/fhir-knative-jvm
    ```

    You see the revision of the Knative application is made available.

    ```
    Replacing service 'myfhir' in namespace 'my-fhir-project':

    0.213s Configuration "myfhir" does not have any ready Revision.
    0.623s ...
    0.762s Configuration "myfhir" is waiting for a Revision to become ready.
    29.217s ...
    29.358s Ingress has not yet been reconciled.
    29.840s Waiting for load balancer to be ready
    30.129s Ready to serve.

    Service 'myfhir' replaced to latest revision 'myfhir-00001' is available at URL:
    http://myfhir-my-fhir-project.healthcare-serverless-9999-0000.us-east.containers.appdomain.cloud
    ```

# **Test**

### 8. Test the Serverless Function to load data

1. Check the url endpoint

    ```
    kn service describe myfhir -o url 
    ```

    You see: 

    ``` 
    http://fhir-loadero-fhir-serverless.0000.us-east.containers.appdomain.cloud
    ```

1. Call the Serverless project with example first and last names.

    ```
    curl $(kn service describe myfhir -o url)'/v1/api/loader?last=Smith&first=John'
    ```

    You see the URLs generated for the new Patient data.

    ```
    [https://fhirserver.appdomain.cloud/fhir-server/api/v4/Patient/dc7857d3-b5b8-4267-bdc5-14f86848ea7c/_history/1, https://fhirserver.appdomain.cloud/fhir-server/api/v4/Observation/17bc7e604ee-556a98d0-08bf-4026-a3cd-b237cca895ca/_history/1, https://fhirserver.appdomain.cloud/fhir-server/api/v4/Medication/b537a83b-0b06-4b07-8332-9801ca7022b2/_history/1, https://fhirserver.appdomain.cloud/fhir-server/api/v4/MedicationAdministration/17bc7e6052c-a074acff-86db-4ef6-9aac-2195eb7e9118/_history/1]
    ```

### 9. Confirm Your data is loaded using FHIR Search

1. Copy the URL from the output. e.g. `https://fhirserver-dev-fhir-serverless.0000.us-east.containers.appdomain.cloud/fhir-server/api/v4/Patient/dc7857d3-b5b8-4267-bdc5-14f86848ea7c/_history/1` and modify it slightly to extract the UUID so you are able to execute an `_id` search. The UUID above is `dc7857d3-b5b8-4267-bdc5-14f86848ea7c`.

1. Confirm the Patient is loaded using IBM FHIR Server's Search support. Be sure to include the URL in Double Quotes.

    ```
    curl -k -u fhiruser:change-password \
        "https://fhirserver-dev-hir-serverless.healthcare-serverless-.us-east.containers.appdomain.cloud/fhir-server/api/v4/Patient?_id=dc7857d3-b5b8-4267-bdc5-14f86848ea7c&_pretty=true"
    ```

    You see the FHIR R4 Patient Resource

    ``` json
    {
        "resourceType": "Bundle",
        "id": "30b39c61-3803-4ece-a502-a676975eb1d5",
        "type": "searchset",
        "total": 1,
        "link": [
            {
                "relation": "self",
                "url": "https://fhirserver.appdomain.cloud/fhir-server/api/v4/Patient?_count=10&_id=dc7857d3-b5b8-4267-bdc5-14f86848ea7c&_page=1"
            }
        ],
        "entry": [
            {
                "fullUrl": "https://fhirserver.appdomain.cloud/fhir-server/api/v4/Patient/dc7857d3-b5b8-4267-bdc5-14f86848ea7c",
                "resource": {
                    "resourceType": "Patient",
                    "id": "dc7857d3-b5b8-4267-bdc5-14f86848ea7c",
                    "meta": {
                        "versionId": "1",
                        "lastUpdated": "2021-09-09T00:12:25.824Z"
                    },
                    "text": {
                        "status": "generated",
                        "div": "<div xmlns=\"http://www.w3.org/1999/xhtml\">loaded from the datastore</div>"
                    },
                    "active": true,
                    "name": [
                        {
                            "family": "John",
                            "given": [
                                "Smith"
                            ]
                        }
                    ],
                    "birthDate": "2021-09-09",
                    "multipleBirthInteger": 2,
                    "generalPractitioner": [
                        {
                            "reference": "urn:uuid:c03f629d-16ff-4644-8ed9-fff14f35730d"
                        }
                    ]
                },
                "search": {
                    "mode": "match",
                    "score": 1
                }
            }
        ]
    }
    ```

You have successfully loaded your IBM FHIR Server with Healthcare Data using a Serverless project.

### 10. Test the Serverless Function to search and read data

1. Call the Serverless project with example first and last names.

    ```
    curl $(kn service describe myfhir -o url)'/v1/api/report'
    ```

    You see the report generated by querying the backend FHIR Data.

    ``` sh
    Total Number of Medication Administrations Today are: 2
    [0] John Smith
    [1] Johnny Smith
    ```

If there is more than one Patient created, you'll notice your report gets slightly more complicated as it reports on the total Patients that have Medications administered to them.

# **Conclusion**

In this lab, you have built a simple healthcare serverless application using Red Hat OpenShift Serverless to interact with the IBM FHIR Server.

# **Contact**

[Paul Bastide](https://bastide.org/) is reachable at:

- [Author: Paul Bastide](mailto://pbastide@us.ibm.com?subject=DDC%3A%20Integrating%20Healthcare%20Data%20in%20a%20Serverless%20World%20Lab)
- [GitHub: Healthcare Serverless Lab - Issues](https://ibm.biz/healthcare-serverless-issues)
- [HL7 Zulip: #ibm](https://chat.fhir.org/#narrow/stream/212434-ibm)

<div id="setup"/>
# **Appendix: Setup**

#### **Setup** IBM Cloud CLI (Optional)

1. Install the [IBM Cloud CLI](https://cloud.ibm.com/docs/cli?topic=cli-getting-started)

2. Install the [IBM Cloud CLI Plugins](https://cloud.ibm.com/docs/openshift?topic=openshift-openshift-cli)

#### *Plugin: Container Service*

You should install Container Service for additional control of your containers.

``` shell
ibmcloud plugin install container-service -f
```

You see:

``` shell
Looking up 'container-service' from repository 'IBM Cloud'...
Plug-in 'container-service/kubernetes-service 1.0.312' found in repository 'IBM Cloud'
Attempting to download the binary file...
29.13 MiB / 29.13 MiB [============] 100.00% 3s
30539808 bytes downloaded
Installing binary...
OK
Plug-in 'container-service 1.0.312' was successfully installed. Use 'ibmcloud plugin show container-service' to show its details.
```

#### *Plugin: Container Registry*

You should install Container Registry to support a custom Registry

``` shell
ibmcloud plugin install container-registry -f
```

You see: 

```
Looking up 'container-registry' from repository 'IBM Cloud'...
Plug-in 'container-registry 0.1.543' found in repository 'IBM Cloud'
Attempting to download the binary file...
26.28 MiB / 26.28 MiB [============] 100.00% 2s
27560704 bytes downloaded
Installing binary...
OK
Plug-in 'container-registry 0.1.543' was successfully installed. Use 'ibmcloud plugin show container-registry' to show its details.
```

#### *Plugin: Observe Service*

You should install Observe Service for additional logging of your containers and cluster.

``` shell
ibmcloud plugin install observe-service -f
```

You see:

```
Looking up 'observe-service' from repository 'IBM Cloud'...
Plug-in 'observe-service 1.0.61' found in repository 'IBM Cloud'
Attempting to download the binary file...
17.70 MiB / 17.70 MiB [============] 100.00% 1s
18561888 bytes downloaded
Installing binary...
OK
Plug-in 'observe-service 1.0.61' was successfully installed. Use 'ibmcloud plugin show observe-service' to show its details.
```

#### **Setup** PostgreSQL Instance

To support the IBM FHIR Server's schema, a [IBM® Cloud Databases for PostgreSQL](https://cloud.ibm.com/databases/databases-for-postgresql/create) database with at least one user with `max_connections` and `max_prepared_transactions` configured as shown in the below script.

The script is as follows:

``` shell
ibmcloud resource service-instance-create serverless-db \
    databases-for-postgresql standard us-east \
    -p '{"service-endpoints": "public-and-private"}'
ibmcloud cdb deployment-configuration \
    serverless-db '{"max_connections": 150}'
ibmcloud cdb deployment-configuration \
    serverless-db '{"max_prepared_transactions": 150}'
ibmcloud cdb deployment-user-create serverless-db \
    fhirserver
```

#### **Setup** IBM FHIR Server Operator

The following outlines the steps necessary to setup the IBM FHIR Server Operator.

1. Setup the IBM Managed OpenShift
2. Install Red Hat OpenShift Serverless Operator
3. Install Knative Serving
4. Add the IBM FHIR Server operator to the catalog
5. Setup the IBM FHIR Server Operator
6. Create an IBM FHIR Server instance
7. Create a Route

#### **1. Setup the IBM Managed OpenShift**

1. Log in to [IBM Cloud](https://cloud.ibm.com/kubernetes/clusters)

1. Click Create Cluster

1. Select Red Hat OpenShift (The page changes to the OpenShift Version)

1. Select the OpenShift verison 4.7 stream (4.7.19 as of writing)

1. Click VPC

1. Select a COS storage container

1. Select only one Worker Zone (we don't need multiple subnets)

1. Change the cluster name to `ibmfhirserverhealthcare`

1. Click Create

1. ... Wait until the Ingress Status is Healthy and there are three nodes deployed and healthy ... 

1. Click on OpenShift Web Console

#### **2. Install Red Hat OpenShift Serverless Operator**

1. Click on **Operators** > **OperatorHub**

1. Find Red Hat OpenShift Serverless

1. Click on the Red Hat OpenShift Serverless tile

1. Click Install 

1. On the Install Operator panel, select Update channel `stable`. 

1. Select Installation mode `All namespaces on the cluster`. 

1. Select Approval strategy `Automatic`

1. Click Install

    ... The operator is installed ... 

1. Wait for Installed Operator - ready for use.

1. Click View Operator

#### **3. Install Knative Serving**

1. Switch Project to the knative-serving namespace

1. Click on **Operators** > **Red Hat OpenShift Serverless**

1. Click **Knative Serving** > **Create Instance**

1. Click Create

#### **4. Add the IBM FHIR Server operator to the catalog**

1. Click on the User dropdown in the Upper Right > Copy Login Command

1. A new tab opens, Click Display Token and Copy the Contents

1. On a terminal window, paste the contents on the terminal.

    ~~~ sh
    oc login --token=sha256~*** \
        --server=https://***.us-east.containers.cloud.ibm.com:32689
    ~~~

    You see: 

    ~~~ sh
    Logged into "https://****.us-east.containers.cloud.ibm.com:32689" as "IAM#***" using the token provided.

    You have access to 69 projects, the list has been suppressed. You can list all projects with 'oc projects'

    Using project "default".
    ~~~

1. Create a file for the IBM Operator Catalog source with the following content, and save as IBMCatalogSource.yml:

    ~~~ yaml
    cat << EOF > IBMCatalogSource.yml
    apiVersion: operators.coreos.com/v1alpha1
    kind: CatalogSource
    metadata:
    name: ibm-operator-catalog
    namespace: openshift-marketplace
    spec:
    displayName: "IBM Operator Catalog"
    publisher: IBM
    sourceType: grpc
    image: docker.io/ibmcom/ibm-operator-catalog
    updateStrategy:
        registryPoll:
        interval: 45m
    EOF
    ~~~

1. Apply the source by using the following command:

    ~~~ sh
    oc apply -f IBMCatalogSource.yml
    ~~~

1. Upon success, you see: 

    ~~~
    catalogsource.operators.coreos.com/ibm-operator-catalog created
    ~~~

#### **5. Setup the IBM FHIR Server Operator**

1. Click on **Operators** > **OperatorHub**

1. Find IBM FHIR Server

1. Click on the `IBM FHIR Server` tile

1. Click Install

1. Select Update channel `v1.1`

1. Select `A specific namespace on the cluster`

1. Click on Installed Namespace

1. Click Create Namespace

1. Enter name `fhir-serverless`

1. Click Create

1. Select Approval strategy `automatic`

1. Click Install

```... The operator is installed ... ```

1. Wait for Installed Operator - ready for use.

1. Click View Operator

#### **6. Create an IBM FHIR Server instance**

1. Download the IBM FHIR Server configuration for Postgres the fhir-server-config.json.  Note, [this version](https://github.com/IBM/FHIR/blob/4.6.1/fhir-server/liberty-config/config/default/fhir-server-config-postgresql.json) is from the 4.6.1 release which matches the Operator's version.

``` sh
curl -o fhir-server-config.json -L \
    https://raw.githubusercontent.com/IBM/FHIR/4.6.1/fhir-server/liberty-config/config/default/fhir-server-config-postgresql.json
```

1. If you look at the configuration, you'll notice it's a JSON file with lots of name-value pairs which control the behavior of the IBM FHIR Server. The details for each of the features and configurations is detailed on the [IBM FHIR Server's Users Guide](https://github.com/IBM/FHIR/blob/4.6.1/docs/src/pages/guides/FHIRServerUsersGuide.md).

1. Create the admin.txt password with a random password value.

``` sh
cat << EOF > admin.txt
$(openssl rand -hex 20)
EOF
```

1. Create the user.txt password

``` sh
cat << EOF > user.txt
$(openssl rand -hex 20)
EOF
```

1. Create the timeout secret

``` sh
cat << EOF > timeout.txt
240s
EOF
```

1. Next, create persistence.json. 

1. Login to the [IBM Cloud Console](https://cloud.ibm.com/resources)

1. Find your Postgres service `serverless-db`

1. Click Service Credentials 

1. Click New Credentials

1. Type `serverless`

1. Click Add

1. Expand the `serverless` entry

1. Replace the details in the command below:

```
cat << EOF > persistence.json
{
      "persistence": [
          {
              "db":  {
                  "type": "postgresql",
                  "host": "example.appdomain.cloud",
                  "port": "13999",
                  "database": "ibmclouddb",
                  "user": "dbuser",
                  "password": "password",
                  "ssl": "true",
                  "certificate_base64": "<<from connection.certificate.certificate_base64>>"
              },
              "schema": {
                  "fhir": "FHIRDATA",
                  "batch": "FHIR_JBATCH",
                  "oauth": "FHIR_OAUTH"
              },
              "grant":  "dbuser",
              "behavior": "onboard"
          }
      ]
}
```

1. Create datasource.xml by replacing the values below

``` sh 
cat << EOF > datasource.xml
 <server>
      <dataSource id="fhirDefaultDefault" jndiName="jdbc/fhir_default_default" type="javax.sql.XADataSource" statementCacheSize="200" syncQueryTimeoutWithTransactionTimeout="true" validationTimeout="30s">
        <jdbcDriver javax.sql.XADataSource="org.postgresql.xa.PGXADataSource" libraryRef="sharedLibPostgres"/>
        <properties.postgresql
            serverName="example.appdomain.cloud"
            portNumber="13999"
            databaseName="ibmclouddb"
            user="fhirserver"
            password="change-password"
            currentSchema="fhirdata"
        />
        <connectionManager maxPoolSize="200" minPoolSize="40"/>
    </dataSource>
</server>
```

1. Create the db.cert

``` sh
echo connection.certificate.certificate_base64>> | base64 -d > db.cert
```

1. Create the `fhir-serverless-secret` secret

``` sh
oc create secret generic fhir-serverless-secret \
      --from-file=IBM_FHIR_SCHEMA_TOOL_INPUT=./persistence.json \
      --from-file=IBM_FHIR_SERVER_CONFIG=./fhir-server-config.json \
      --from-file=IBM_FHIR_SERVER_DATASOURCE=./datasource.xml \
      --from-file=IBM_FHIR_SERVER_ADMIN_PASSWORD=./admin.txt \
      --from-file=IBM_FHIR_SERVER_USER_PASSWORD=./user.txt \
      --from-file=IBM_FHIR_SERVER_CERT=./db.cert \
      --from-file=IBM_FHIR_SERVER_TRANSACTION_TIMEOUT=./timeout.txt \
      --namespace=fhir-serverless
```

1. Click Create Instance on the `IBMFHIRServer` tile.

1. Enter a name for the instance, and enter the name of the Secret resource containing the IBM FHIR Server configuration, such as `fhir-serverless-inst`.

1. Set License accept to true.

1. Click Create

1. Once the pods are UP... 

1. Create a **Networking** > **Routes**

1. Click **Create Route**

1. Enter a Name - `fhir-server-route`

1. Select Service `fhir-serverless-inst`

1. Select Target Port `443 -> 9443`.

1. Select TLS Termination 

1. Click TLS Termination Type `Passthrough`

1. Select Insecure Traffic `Redirect`

1. Click Create

1. You are redirected to the **Route details**, and you find your location listed:

    `https://fhir-server-route-fhir-serverless.healthcare-serverless-0000.example.appdomain.cloud`

1. Run a Health Check 

```
curl -k -u fhiruser:yourpassword 'https://fhirserver-dev-fhir-serverless.0000.us-east.containers.appdomain.cloud/fhir-server/api/v4/$healthcheck'
```

Your IBM FHIR Server instance is up and running on Red Hat OpenShift.

# **References and Links**

The following references are handy for fellow developers:

#### **IBM FHIR Server**

- [IBM FHIR Server](https://www.ibm.com/products/fhir-server)
- [GitHub: IBM/FHIR](https://github.com/IBM/FHIR)
- [IBM FHIR Server Operator](https://ibm.github.io/FHIR/cloudpak/)
- [Docker: IBM FHIR Server](https://hub.docker.com/r/ibmcom/ibm-fhir-server)
- [Docker: IBM FHIR Server Schema Tool](https://hub.docker.com/r/ibmcom/ibm-fhir-schematool)
- [Project Alvearie](https://alvearie.io/)

#### **Import Links**

- [GitHub: Healthcare Serverless Lab](https://ibm.biz/ibm-fhir-server-healthcare-serverless)

#### **Red Hat OpenShift**

- [IBM Managed OpenShift](https://www.ibm.com/cloud/openshift)
- [OpenShift Serverless](https://cloud.redhat.com/learn/topics/serverless)
- [Why OpenShift Serverless](https://cloud.redhat.com/blog/why-and-when-you-need-to-consider-openshift-serverless)

#### **Knative**

- [OReilly: Knative Cookbook](https://developers.redhat.com/books/knative-cookbook)
- [Red Hat OpenShift Container Platform: Understanding Knative Serving](https://docs.openshift.com/container-platform/4.8/serverless/knative_serving/serverless-understanding-serving.html)
- [OpenShift Serverless: Functions Quickstart](https://openshift-knative.github.io/docs/docs/functions/quickstart-functions.html)
- [Quick Start Knative](https://www.mohashi.io/blog/kubernetes/knative/setup-a-cloud-native-quarkus-app-in-knative/)

# Acknowledgements

The author would like to acknowledge the authors of [Digital Developer Conference Hybrid Cloud - Lab Stream](https://ibm-developer.gitbook.io/digital-developer-conference-hybrid-cloud/serverless-workshop/lab) and [GitHub: IBM/example-bank - Building a data privacy focused mobile back-end](https://github.com/IBM/example-bank) who provided insight and a pattern to follow for this Lab.
