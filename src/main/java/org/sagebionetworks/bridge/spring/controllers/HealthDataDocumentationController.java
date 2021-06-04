package org.sagebionetworks.bridge.spring.controllers;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.HealthDataDocumentation;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.services.HealthDataDocumentationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.Charset;

import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;

@CrossOrigin
@RestController
public class HealthDataDocumentationController extends BaseController {
    private static final Long MAX_DOCUMENTATION_BYTES = 1024L * 100;

    private HealthDataDocumentationService healthDataDocumentationService;

    @Autowired
    public final void setHealthDataDocumentationService(HealthDataDocumentationService healthDataDocumentationService) {
        this.healthDataDocumentationService = healthDataDocumentationService;
    }

    /** Create or update a health data documentation. */
    @PostMapping(path="/v3/healthdataDocumentation")
    public HealthDataDocumentation createOrUpdateHealthDataDocumentation() {
        UserSession session = getAuthenticatedSession(Roles.RESEARCHER, Roles.DEVELOPER);

        HealthDataDocumentation documentation = parseJson(HealthDataDocumentation.class);

        if (documentation.getDocumentation() == null) {
            throw new BadRequestException("Documentation is required to store health data documentation.");
        }

        // check documentation size (max allowed is 100 KB)
        if (documentation.getDocumentation().getBytes(Charset.defaultCharset()).length > MAX_DOCUMENTATION_BYTES) {
            throw new BadRequestException("Documentation must be less than 100 KB");
        }

        // update health data documentation attributes
        if (documentation.getCreatedOn() == null) {
            documentation.setCreatedOn(DateTime.now());
            documentation.setCreatedBy(session.getId());
        } else {
            documentation.setModifiedOn(DateTime.now());
            documentation.setModifiedBy(session.getId());
        }

        return healthDataDocumentationService.createOrUpdateHealthDataDocumentation(documentation);
    }

    /** Get a health data documentation with the given identifier. */
    @GetMapping(path="/v3/healthdataDocumentation/{identifier}")
    public HealthDataDocumentation getHealthDataDocumentationForId(@PathVariable String identifier) {
        UserSession session = getAuthenticatedSession(Roles.RESEARCHER, Roles.DEVELOPER);

        // AppId is a placeholder for parentId. In the future, for study-scoped documentation, it would be “{appId}:study:{studyId}”
        // and for Assessments it would be “{appId}:assessment:{assessmentId}”.
        String parentId = session.getAppId();
        return healthDataDocumentationService.getHealthDataDocumentationForId(identifier, parentId);
    }

    /** Get all health data documentation with the given parentId. */
    @GetMapping(path="/v3/healthdataDocumentation")
    public ForwardCursorPagedResourceList<HealthDataDocumentation> getAllHealthDataDocumentationForParentId(@RequestParam String parentId,
        @RequestParam(required = false) String pageSize, @RequestParam(required = false) String offsetKey) {
        getAuthenticatedSession(Roles.RESEARCHER, Roles.DEVELOPER);

        int pageSizeInt = BridgeUtils.getIntOrDefault(pageSize, API_DEFAULT_PAGE_SIZE);

        return healthDataDocumentationService.getAllHealthDataDocumentation(parentId, pageSizeInt, offsetKey);
    }

    /** Delete a health data documentation with the given identifier. */
    @DeleteMapping(path="v3/healthdataDocumentation/{identifier}")
    public StatusMessage deleteHealthDataDocumentationForIdentifier(@PathVariable String identifier) {
        UserSession session = getAuthenticatedSession(Roles.RESEARCHER, Roles.DEVELOPER);

        String parentId = session.getAppId(); // placeholder
        healthDataDocumentationService.deleteHealthDataDocumentation(identifier, parentId);

        return new StatusMessage("Health data documentation has been deleted for the given identifier.");
    }

    /** Delete all health data documentation with the given parentId. */
    @DeleteMapping(path="/v3/healthdataDocumentation")
    public StatusMessage deleteAllHealthDataDocumentationForParentId(@RequestParam String parentId) {
        getAuthenticatedSession(Roles.RESEARCHER, Roles.DEVELOPER);

        healthDataDocumentationService.deleteAllHealthDataDocumentation(parentId);
        return new StatusMessage("Health data documentation has been deleted.");
    }
}