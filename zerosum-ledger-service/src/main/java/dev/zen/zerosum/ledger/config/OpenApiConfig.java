package dev.zen.zerosum.ledger.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI zerosumOpenApi() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("Zerosum Ledger API")
                                .version("0.1.0")
                                .description(
                                        """
                        Double-entry ledger. Amounts are integer minor units. \
                        POST /transfers is idempotent: send an Idempotency-Key header and \
                        retries never post twice. Errors are RFC 9457 problem details."""));
    }
}
