package com.ktpm.common;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.*;
import org.springframework.context.annotation.*;

@Configuration
public class OpenApiConfig {
  @Bean
  OpenAPI api() {
    return new OpenAPI()
        .info(
            new Info()
                .title("KTPM Auction API — Phase 1")
                .version("1.0")
                .description(
                    "JWT Bearer authentication. Money is decimal with at most 2 fractional digits. Times are ISO-8601 UTC. Lists use page=0&size=20 (max 100). Public GET endpoints may be called without a token."))
        .components(
            new Components()
                .addSecuritySchemes(
                    "bearerAuth",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT"))
                .addSchemas(
                    "ApiError",
                    new io.swagger.v3.oas.models.media.ObjectSchema()
                        .addProperty(
                            "timestamp",
                            new io.swagger.v3.oas.models.media.StringSchema().format("date-time"))
                        .addProperty("status", new io.swagger.v3.oas.models.media.IntegerSchema())
                        .addProperty("error", new io.swagger.v3.oas.models.media.StringSchema())
                        .addProperty("message", new io.swagger.v3.oas.models.media.StringSchema())
                        .addProperty("path", new io.swagger.v3.oas.models.media.StringSchema())))
        .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
  }

  @Bean
  org.springdoc.core.customizers.OperationCustomizer operationContracts() {
    return (operation, handler) -> {
      String controller = handler.getBeanType().getSimpleName();
      String method = handler.getMethod().getName();
      boolean publicEndpoint =
          switch (controller) {
            case "AuthController" -> java.util.Set.of("register", "login").contains(method);
            case "AuctionController" -> java.util.Set.of("list", "get").contains(method);
            case "BiddingController" -> "history".equals(method);
            default -> false;
          };
      if (publicEndpoint) operation.setSecurity(java.util.List.of());
      java.util.Map<String, String> errors =
          java.util.Map.of(
              "400", "Invalid request or validation failure",
              "401", "Missing, invalid, expired or revoked token",
              "403", "Role or ownership denied",
              "404", "Resource not found",
              "409", "Business state, money or concurrency conflict",
              "500", "Unexpected server error");
      errors.forEach(
          (code, description) ->
              operation
                  .getResponses()
                  .addApiResponse(
                      code,
                      new io.swagger.v3.oas.models.responses.ApiResponse()
                          .description(description)
                          .content(
                              new io.swagger.v3.oas.models.media.Content()
                                  .addMediaType(
                                      "application/json",
                                      new io.swagger.v3.oas.models.media.MediaType()
                                          .schema(
                                              new io.swagger.v3.oas.models.media.Schema<>()
                                                  .$ref("#/components/schemas/ApiError"))))));
      return operation;
    };
  }
}
