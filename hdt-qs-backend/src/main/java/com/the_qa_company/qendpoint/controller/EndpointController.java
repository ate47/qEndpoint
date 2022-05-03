package com.the_qa_company.qendpoint.controller;

import com.the_qa_company.qendpoint.utils.io.MultipartStreamExtractor;
import org.apache.tomcat.util.http.fileupload.MultipartStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebInputException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

@CrossOrigin(origins = "http://localhost:3001")
@RestController
@RequestMapping("/api/endpoint")
public class EndpointController {
    private static final Logger logger = LoggerFactory.getLogger(EndpointController.class);

    @Autowired
    Sparql sparql;

    @RequestMapping(value = "/sparql")
    public void sparqlEndpoint(
            @RequestParam(value = "query", required = false) final String query,
            @RequestParam(value = "update", required = false) final String updateQuery,
            @RequestParam(value = "format", defaultValue = "json") final String format,
            @RequestHeader(value = "Accept", defaultValue = "application/sparql-results+json") String acceptHeader,
            @RequestHeader(value = "timeout", defaultValue = "300") int timeout,
            @RequestHeader(value = "Content-Type", defaultValue = "text/plain") String content,

            @RequestBody(required = false) String body,
            HttpServletResponse response
    )
            throws IOException {
        logger.info("New query");

        if (query != null) {
            sparql.execute(query, timeout, acceptHeader, response::setContentType, response.getOutputStream());
        } else if (body != null && content.equals("application/sparql-query")) {
			sparql.execute(body, timeout, acceptHeader, response::setContentType, response.getOutputStream());
		} else if (updateQuery != null) {
			sparql.executeUpdate(updateQuery, timeout, response.getOutputStream());
		} else if (body != null) {
			sparql.executeUpdate(body, timeout, response.getOutputStream());
        } else {
            throw new ServerWebInputException("Query not specified");
        }
    }

    @RequestMapping(value = "/update")
    public void sparqlUpdate(
            @RequestParam(value = "query") final String query,
            @RequestParam(value = "format", defaultValue = "json") final String format,
            @RequestHeader(value = "Accept", defaultValue = "application/sparql-results+json") String acceptHeader,
            @RequestParam(value = "timeout", defaultValue = "5") int timeout,
			HttpServletResponse response)
            throws IOException {
        logger.info("Query " + query);
        logger.info("timeout: " + timeout);
        if (format.equals("json")) {
			sparql.executeUpdate(query, timeout, response.getOutputStream());
        } else {
            throw new ServerWebInputException("Format not supported");
        }
    }

    @PostMapping(value = "/load", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Sparql.LoadFileResult> load(HttpServletRequest request) throws IOException, InterruptedException {
        try (MultipartStreamExtractor extractor = new MultipartStreamExtractor(request)) {
            while (extractor.hasNext()) {
                MultipartStreamExtractor.ExtractedStream stream = extractor.next();
                if (stream.getName().equals("file")) {
                    String filename = stream.getFilename();
                    InputStream is = stream.readToStream();

                    logger.info("Trying to index {}", filename);
                    Sparql.LoadFileResult out = sparql.loadFile(is, filename);
                    return ResponseEntity.status(HttpStatus.OK).body(out);
                }
            }
        }
        throw new ServerWebInputException("no stream field");
    }

    @GetMapping("/merge")
    public ResponseEntity<Sparql.MergeRequestResult> mergeStore() throws IOException {
        return ResponseEntity.status(HttpStatus.OK).body(sparql.askForAMerge());
    }

    @GetMapping("/reindex")
    public ResponseEntity<Sparql.LuceneIndexRequestResult> reindex() throws Exception {
        return ResponseEntity.status(HttpStatus.OK).body(sparql.reindexLucene());
    }

    @GetMapping("/is_merging")
    public ResponseEntity<Sparql.IsMergingResult> isMerging() throws IOException {
        return ResponseEntity.status(HttpStatus.OK).body(sparql.isMerging());
    }
    @GetMapping("/")
    public ResponseEntity<String> home() {
        return ResponseEntity.status(HttpStatus.OK).body("ok");
    }
}
