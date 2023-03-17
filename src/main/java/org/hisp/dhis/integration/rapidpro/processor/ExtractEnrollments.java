package org.hisp.dhis.integration.rapidpro.processor;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.json.JSONObject;
import org.springframework.stereotype.Component;


@Component
public class ExtractEnrollments implements Processor
{

    @Override
    public void process( Exchange exchange )
        throws Exception
    {
        String messageBody = exchange.getIn().getBody(String.class);
        JSONObject jsonBody = new JSONObject(messageBody);
        String instances = jsonBody.getJSONArray("instances").toString();
        exchange.getMessage().setBody(instances);
    }
}

