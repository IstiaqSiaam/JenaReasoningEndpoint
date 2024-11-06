package com.example.jena;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.any23.Any23;
import org.apache.any23.extractor.ExtractionException;
import org.apache.any23.http.HTTPClient;
import org.apache.any23.source.DocumentSource;
import org.apache.any23.source.HTTPDocumentSource;
import org.apache.any23.writer.NTriplesWriter;
import org.apache.any23.writer.TripleHandler;
import org.apache.any23.writer.TripleHandlerException;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QueryParseException;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.rdf.model.*;
import org.apache.jena.reasoner.*;
import org.apache.jena.reasoner.rulesys.*;
import org.apache.jena.util.FileManager;
import org.apache.jena.query.Query;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.jena.query.QuerySolution;
import org.apache.jena.rdf.model.RDFNode;

/**
 * Servlet implementation class ReasonerServlet
 */
@WebServlet("/reasoner")
public class ReasonerServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    public ReasonerServlet() {
        super();
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // Get parameters from the request
        String sourceUrl = request.getParameter("sourceUrl");
        String rulesStr = request.getParameter("rules");
        String sparqlQuery = request.getParameter("sparqlQuery");

        response.setContentType("text/html");
        PrintWriter out = response.getWriter();

        try {
            // Parse RDFa content from XHTML file
            Model modelFromFile = parseRDFaContent(sourceUrl);

            // Apply rules if provided
            InfModel infModel = null;
            if (rulesStr != null && !rulesStr.trim().isEmpty()) {
                infModel = applyRules(modelFromFile, rulesStr);
            } else {
                infModel = ModelFactory.createInfModel(ReasonerRegistry.getRDFSSimpleReasoner(), modelFromFile);
            }

            // Execute SPARQL query
            executeSPARQLQuery(infModel, sparqlQuery, out);

        } catch (Exception e) {
            out.println("<p>Error: " + e.getMessage() + "</p>");
            e.printStackTrace();
        }
    }

    private Model parseRDFaContent(String sourceUrl) throws IOException, ExtractionException, TripleHandlerException, URISyntaxException {
        Any23 runner = new Any23();
        runner.setHTTPUserAgent("test-user-agent");
        HTTPClient httpClient = runner.getHTTPClient();
        DocumentSource source = new HTTPDocumentSource(httpClient, sourceUrl);
        Model model = ModelFactory.createDefaultModel();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            TripleHandler handler = new NTriplesWriter(out);
            runner.extract(source, handler);
            handler.close();
            String ntriples = out.toString();
            model.read(new StringReader(ntriples), null, "N-TRIPLES");
        }
        return model;
    }

    private InfModel applyRules(Model baseModel, String rulesStr) throws Rule.ParserException {
        // Parse rules from string
        List<Rule> rules = Rule.parseRules(rulesStr);
        // Create a reasoner
        Reasoner reasoner = new GenericRuleReasoner(rules);
        reasoner.setDerivationLogging(true);
        // Create an inferred model
        InfModel infModel = ModelFactory.createInfModel(reasoner, baseModel);
        return infModel;
    }

    private void executeSPARQLQuery(Model model, String sparqlQuery, PrintWriter out) {
        try {
            Query query = QueryFactory.create(sparqlQuery);
            try (QueryExecution qexec = QueryExecutionFactory.create(query, model)) {
                if (query.isSelectType()) {
                    ResultSet resultSet = qexec.execSelect();

                    // Start HTML table
                    out.println("<table border='1'>");

                    // Print table header
                    out.println("<tr>");
                    List<String> varNames = resultSet.getResultVars();
                    for (String varName : varNames) {
                        out.println("<th>" + varName + "</th>");
                    }
                    out.println("</tr>");

                    // Iterate over the results
                    while (resultSet.hasNext()) {
                        QuerySolution qs = resultSet.next();
                        out.println("<tr>");
                        for (String varName : varNames) {
                            RDFNode value = qs.get(varName);
                            out.println("<td>" + (value != null ? value.toString() : "") + "</td>");
                        }
                        out.println("</tr>");
                    }

                    // End HTML table
                    out.println("</table>");

                } else if (query.isConstructType()) {
                    Model resultModel = qexec.execConstruct();
                    resultModel.write(out, "TURTLE");
                } else if (query.isDescribeType()) {
                    Model resultModel = qexec.execDescribe();
                    resultModel.write(out, "TURTLE");
                } else if (query.isAskType()) {
                    boolean result = qexec.execAsk();
                    out.println("<p>ASK query result: " + result + "</p>");
                } else {
                    out.println("<p>Unsupported query type.</p>");
                }
            }
        } catch (QueryParseException e) {
            out.println("<p>SPARQL Query Parsing Error: " + e.getMessage() + "</p>");
        }
    }

}
