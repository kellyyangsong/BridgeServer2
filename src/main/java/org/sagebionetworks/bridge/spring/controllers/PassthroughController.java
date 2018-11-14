package org.sagebionetworks.bridge.spring.controllers;

import java.io.IOException;
import java.util.Enumeration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.servlet.http.HttpServletRequest;

import com.google.common.base.Charsets;
import com.google.common.base.Stopwatch;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.sagebionetworks.bridge.config.Config;

/**
 * Pass-through controller. Takes in HTTP requests that aren't caught by any other controller and forwards them to
 * BridgePF.
 */
@CrossOrigin
@RestController
public class PassthroughController {
    private static final Logger LOG = LoggerFactory.getLogger(PassthroughController.class);

    static final String CONFIG_KEY_BRIDGE_PF_HOST = "bridge.pf.host";
    static final String HEADER_REQUEST_ID = "X-Request-Id";

    private String bridgePfHost;

    /** Bridge config. */
    @Autowired
    public void setConfig(Config config) {
        bridgePfHost = config.get(CONFIG_KEY_BRIDGE_PF_HOST);
    }

    /** Passthrough handler. */
    @RequestMapping
    public ResponseEntity<String> handleDefault(HttpServletRequest request, @RequestBody(required = false) String body)
            throws IOException {
        Stopwatch stopwatch = Stopwatch.createStarted();

        // URL. This includes query parameters. Spring provides them to use as a string, so we just append them to the
        // url like a string.
        String url = request.getRequestURI();
        LOG.info("Received request " + request.getMethod() + " " + url);

        String fullUrl = bridgePfHost + url;
        if (request.getQueryString() != null) {
            fullUrl += "?" + request.getQueryString();
        }

        // Method. Note that Apache HTTP has different function calls for each HTTP method, so we need this switch.
        Request pfRequest;
        switch (request.getMethod()) {
            case "GET":
                pfRequest = Request.Get(fullUrl);
                break;
            case "POST":
                pfRequest = Request.Post(fullUrl);
                break;
            case "DELETE":
                pfRequest = Request.Delete(fullUrl);
                break;
            default:
                String errorMessage = "Method " + request.getMethod() + " not supported";
                LOG.warn(errorMessage);
                return ResponseEntity.badRequest().build();
        }

        // Headers.
        Enumeration<String> headerNameEnum = request.getHeaderNames();
        String requestId = null;
        while (headerNameEnum.hasMoreElements()) {
            String headerName = headerNameEnum.nextElement();
            if (headerName.equalsIgnoreCase("content-length")) {
                // Request.body() automatically sets this header for us. We can't set it here, or else we'll get a
                // "header already present" exception.
                continue;
            }

            String headerValue = request.getHeader(headerName);
            pfRequest.addHeader(headerName, headerValue);

            if (headerName.equalsIgnoreCase(HEADER_REQUEST_ID)) {
                requestId = headerValue;
            }
        }

        // Add request ID header, if it doesn't exist.
        if (requestId == null) {
            requestId = randomGuid();
            pfRequest.addHeader(HEADER_REQUEST_ID, requestId);
        }

        LOG.info("Sending request " + request.getMethod() + " " + url + " w/ requestId=" + requestId);

        // Request body. We take in the raw body as a string and pass it along to BridgePF.
        if (body != null) {
            pfRequest.bodyString(body, ContentType.parse(request.getContentType()));
        }

        // Execute.
        HttpResponse pfResponse = pfRequest.execute().returnResponse();

        // Response status.
        int statusCode = pfResponse.getStatusLine().getStatusCode();
        HttpStatus springStatus = HttpStatus.resolve(statusCode);
        if (springStatus == null) {
            LOG.error("Unrecognized status code " + statusCode);
            return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
        }

        // Response headers.
        HttpHeaders springHeaders = new HttpHeaders();
        for (Header header : pfResponse.getAllHeaders()) {
            springHeaders.add(header.getName(), header.getValue());
        }

        // Response body.
        String responseBody = EntityUtils.toString(pfResponse.getEntity(), Charsets.UTF_8);

        LOG.info("Request " + requestId + ", status=" + statusCode + ", took " +
                stopwatch.elapsed(TimeUnit.MILLISECONDS) + "ms");
        return new ResponseEntity<>(responseBody, springHeaders, springStatus);
    }

    // Package-scoped to allow unit tests to spy.
    String randomGuid() {
        return UUID.randomUUID().toString();
    }
}
