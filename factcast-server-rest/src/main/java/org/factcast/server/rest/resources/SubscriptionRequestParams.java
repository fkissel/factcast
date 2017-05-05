package org.factcast.server.rest.resources;

import java.util.List;
import java.util.UUID;

import javax.ws.rs.QueryParam;

import org.factcast.core.spec.FactSpec;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.server.rest.resources.converter.JsonParam;
import org.hibernate.validator.constraints.NotEmpty;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SubscriptionRequestParams {
    @QueryParam("from")
    private String from;

    @QueryParam("follow")
    private boolean follow;

    @NotEmpty
    @QueryParam("factSpec")
    @JsonParam
    private List<FactSpec> factSpec;

    public SubscriptionRequestTO toRequest(ObjectMapper objectMapper) {

        SubscriptionRequestTO r = new SubscriptionRequestTO();
        r.continous(follow);
        if (from != null) {
            r.startingAfter(UUID.fromString(from));
        }

        r.addSpecs(factSpec);
        return r;
    }
}
