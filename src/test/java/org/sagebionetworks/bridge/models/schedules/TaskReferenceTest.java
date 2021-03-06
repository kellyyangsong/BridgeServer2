package org.sagebionetworks.bridge.models.schedules;

import static org.testng.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import nl.jqno.equalsverifier.EqualsVerifier;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class TaskReferenceTest {
    private static final SchemaReference SCHEMA_REF = new SchemaReference("test-schema", 42);

    @Test
    public void equalsVerifier() {
        EqualsVerifier.forClass(TaskReference.class).allFieldsShouldBeUsed().verify();
    }

    @Test
    public void testToString() {
        // SchemaReference.toString() is already tested elsewhere. Use that string directly so we don't have to depend
        // on another class's implementaiton.
        TaskReference taskRef = new TaskReference("test-task", SCHEMA_REF);
        assertEquals(taskRef.toString(), "TaskReference [identifier=test-task, schema=" + SCHEMA_REF.toString() + "]");
    }

    @Test
    public void jsonSerialization() throws Exception {
        // Similarly, use the JSON for SchemaReference here as well.
        // Start with JSON.
        String jsonText = "{\n" +
                "   \"identifier\":\"json-task\",\n" +
                "   \"schema\":" + BridgeObjectMapper.get().writeValueAsString(SCHEMA_REF) + "\n" +
                "}";

        // Convert to POJO and validate.
        TaskReference taskRef = BridgeObjectMapper.get().readValue(jsonText, TaskReference.class);
        assertEquals(taskRef.getIdentifier(), "json-task");
        assertEquals(taskRef.getSchema(), SCHEMA_REF);

        // Convert back to JSON and validate.
        JsonNode jsonNode = BridgeObjectMapper.get().convertValue(taskRef, JsonNode.class);
        assertEquals(jsonNode.size(), 3);
        assertEquals(jsonNode.get("identifier").textValue(), "json-task");
        assertEquals(jsonNode.get("schema"), BridgeObjectMapper.get().convertValue(SCHEMA_REF, JsonNode.class));
        assertEquals(jsonNode.get("type").textValue(), "TaskReference");
    }
}
