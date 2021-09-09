/*
 * (C) Copyright IBM Corp. 2021
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ibm.fhir.example;

import static com.ibm.fhir.model.type.String.string;

import java.io.StringWriter;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.ibm.fhir.model.format.Format;
import com.ibm.fhir.model.generator.FHIRGenerator;
import com.ibm.fhir.model.resource.Bundle;
import com.ibm.fhir.model.resource.Bundle.Entry.Request;
import com.ibm.fhir.model.resource.Medication;
import com.ibm.fhir.model.resource.Medication.Batch;
import com.ibm.fhir.model.resource.Medication.Ingredient;
import com.ibm.fhir.model.resource.MedicationAdministration;
import com.ibm.fhir.model.resource.MedicationAdministration.Dosage;
import com.ibm.fhir.model.resource.MedicationAdministration.Performer;
import com.ibm.fhir.model.resource.Observation;
import com.ibm.fhir.model.resource.Observation.Component;
import com.ibm.fhir.model.resource.OperationOutcome.Issue;
import com.ibm.fhir.model.resource.Patient;
import com.ibm.fhir.model.type.Code;
import com.ibm.fhir.model.type.CodeableConcept;
import com.ibm.fhir.model.type.Coding;
import com.ibm.fhir.model.type.Date;
import com.ibm.fhir.model.type.DateTime;
import com.ibm.fhir.model.type.Decimal;
import com.ibm.fhir.model.type.HumanName;
import com.ibm.fhir.model.type.Narrative;
import com.ibm.fhir.model.type.Quantity;
import com.ibm.fhir.model.type.Ratio;
import com.ibm.fhir.model.type.Reference;
import com.ibm.fhir.model.type.SimpleQuantity;
import com.ibm.fhir.model.type.Uri;
import com.ibm.fhir.model.type.Xhtml;
import com.ibm.fhir.model.type.code.BundleType;
import com.ibm.fhir.model.type.code.HTTPVerb;
import com.ibm.fhir.model.type.code.MedicationAdministrationStatus;
import com.ibm.fhir.model.type.code.NarrativeStatus;
import com.ibm.fhir.model.type.code.ObservationStatus;
import com.ibm.fhir.validation.FHIRValidator;

/**
 * Generates Sample Data for the Patient using the IBM FHIR Server fhir-model builders.
 */
public class BundleGenerator {

    public BundleGenerator() {
        // No Operation
    }

    /**
     * builds sample data of a patient, observation, medication and medication administration 
     * These resources build references each other.
     * 
     * @param firstName
     * @param lastName
     * @return
     */
    public Bundle sampleData(String firstName, String lastName) {
        String patientUuid = UUID.randomUUID().toString();
        String medicationUuid = UUID.randomUUID().toString();
        Bundle.Entry patient = Bundle.Entry.builder()
                .resource(buildPatient(patientUuid, firstName, lastName))
                .request(
                    Request.builder()
                        .method(HTTPVerb.PUT)
                        .url(Uri.of("Patient/" + patientUuid))
                        .id(patientUuid)
                        .build())
                .build();
        Bundle.Entry observation = Bundle.Entry.builder()
                .resource(buildObservation("1-2-3-4"))
                .request(Request.builder().method(HTTPVerb.POST)
                    .url(Uri.of("Observation")).build())
                .build();
        Bundle.Entry medication = Bundle.Entry.builder()
                .resource(buildMedication(medicationUuid))
                .request(
                    Request.builder()
                        .method(HTTPVerb.PUT)
                        .url(Uri.of("Medication/" + medicationUuid))
                        .id(medicationUuid)
                        .build())
                .build();
        Bundle.Entry medicationAdministration = Bundle.Entry.builder()
                .resource(buildMedicationAdministration(patientUuid, medicationUuid))
                .request(Request.builder().method(HTTPVerb.POST)
                    .url(Uri.of("MedicationAdministration")).build())
                .build();

        return Bundle.builder()
                .type(BundleType.TRANSACTION)
                .entry(
                        patient,
                        observation,
                        medication,
                        medicationAdministration
                    ).build();
    }

    /**
     * build a patient resource with the given names.
     * 
     * @param id
     * @param firstName the patient's firstName
     * @param lastName the patient's lastName
     * @return
     */
    private Patient buildPatient(String id, String firstName, String lastName) {
        com.ibm.fhir.model.type.String given =
                com.ibm.fhir.model.type.String.builder()
                .value(firstName)
                .build();

        HumanName name =
                HumanName.builder()
                    .given(given)
                    .family(string(lastName)).build();

        java.lang.String uUID = UUID.randomUUID().toString();

        Reference providerRef =
                Reference.builder().reference(string("urn:uuid:" + uUID)).build();

        return Patient.builder()
                .id(id)
                .active(com.ibm.fhir.model.type.Boolean.TRUE)
                .multipleBirth(com.ibm.fhir.model.type.Integer.of(2))
                .name(name)
                .birthDate(Date.of(LocalDate.now()))
                .generalPractitioner(providerRef)
                .text(
                    Narrative.builder()
                        .div(Xhtml.of("<div xmlns=\"http://www.w3.org/1999/xhtml\">loaded from the datastore</div>"))
                        .status(NarrativeStatus.GENERATED).build())
                .build();
    }

    /**
     * Generate a simple blood pressure reading
     * 
     * @param patientId to associate the Observation with (subject)
     * @return
     */
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

    /**
     * The Medication that is referenced when a patient administers a medication. 
     * 
     * @param medicationUuid
     * @return
     */
    public Medication buildMedication(String medicationUuid) {
        Medication.Builder builder = Medication.builder();
        builder.id(medicationUuid);
        builder.code(
            CodeableConcept.builder()
                .coding(
                    Coding.builder()
                        .code(Code.of("55284-4"))
                        .system(Uri.of("http://hl7.org/fhir/sid/ndc"))
                        .display(string("Acetaminophen"))
                    .build())
                .text(string("Acetaminophen"))
            .build());
        builder.code(
            CodeableConcept.builder()
                .coding(Coding.builder()
                    .system(Uri.of("http://snomed.info/sct"))
                    .code(Code.of("385057009"))
                    .display(string("Film-coated tablet (qualifier value)"))
                    .build())
                .text(string("Film-coated tablet (qualifier value)"))
            .build());
        builder.ingredient(
            Ingredient.builder()
                .isActive(com.ibm.fhir.model.type.Boolean.TRUE)
                .item(
                    CodeableConcept.builder()
                        .coding(
                            Coding.builder()
                                .system(Uri.of("http://www.nlm.nih.gov/research/umls/rxnorm"))
                                .code(Code.of("315266"))
                                .display(string("Acetaminophen 500 MG"))
                                .build())
                        .build())
                .strength(
                    Ratio.builder()
                        .numerator(
                            Quantity.builder()
                                .value(Decimal.of("25"))
                                .system(Uri.of("http://unitsofmeasure.org"))
                                .code(Code.of("mg"))
                                .build())
                        .denominator(
                            Quantity.builder()
                                .value(Decimal.of("1"))
                                .system(Uri.of("http://terminology.hl7.org/CodeSystem/v3-orderableDrugForm"))
                                .code(Code.of("Tab"))
                                .build())
                        .build())
                .build());
        builder.batch(
            Batch.builder()
                .expirationDate(DateTime.of("2030-01-01"))
                .lotNumber(string("947100100"))
                .build());
        builder.text(
            Narrative.builder()
                .div(Xhtml.of("<div xmlns=\"http://www.w3.org/1999/xhtml\">GENERATED</div>"))
                .status(NarrativeStatus.GENERATED)
                .build());
        return builder.build();
    }

    /**
     * builds a medication administration. 
     * 
     * @param subject the patient's id to associate the MedicationAdministration
     * @param medication the medication referenced in the MedicationAdministration
     * @return
     */
    public MedicationAdministration buildMedicationAdministration(String subject, String medication) {
        return MedicationAdministration.builder()
            .text(
                Narrative.builder()
                    .div(Xhtml.of("<div xmlns=\"http://www.w3.org/1999/xhtml\">GENERATED</div>"))
                    .status(NarrativeStatus.GENERATED)
                .build())
            .status(MedicationAdministrationStatus.COMPLETED)
            .medication(Reference.builder().reference(string("Medication/" + medication)).build())
            .subject(Reference.builder().reference(string("Patient/" + subject)).build())
            .effective(DateTime.now())
            .performer(
                Performer.builder()
                    .actor(Reference.builder().reference(string("Patient/" + subject)).build())
                    .build())
            .dosage(
                Dosage.builder()
                    .dose(
                        SimpleQuantity.builder()
                            .value(Decimal.of("1"))
                            .build())
                .build())
            .build();
    }

    /**
     * generates sample data in a bundle
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        BundleGenerator generator = new BundleGenerator();
        Bundle bundle = generator.sampleData("Joe", "Doe");
        List<Issue> issuesBundle = FHIRValidator.validator().validate(bundle);
        issuesBundle.stream().forEach(System.out::println);

        try(StringWriter writer = new StringWriter()){
            FHIRGenerator.generator(Format.JSON, true).generate(bundle, writer);
            System.out.println(writer.toString());
        }
    }
}