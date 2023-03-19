package org.globex.retail.kubernetes;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Base64;

@ApplicationScoped
public class KubernetesRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(KubernetesRunner.class);

    @Inject
    KubernetesClient client;

    public int run() {

        String matrixNamespace = System.getenv("MATRIX_NAMESPACE");
        if (matrixNamespace == null || matrixNamespace.isBlank()) {
            LOGGER.error("Environment variable 'MATRIX_NAMESPACE' for matrix namespace not set. Exiting...");
            return -1;
        }

        String namespace = System.getenv("NAMESPACE");
        if (namespace == null || namespace.isBlank()) {
            LOGGER.error("Environment variable 'NAMESPACE' for namespace not set. Exiting...");
            return -1;
        }

        String matrixServerUrl = System.getenv("MATRIX_SERVER_URL");
        if (matrixServerUrl == null || matrixServerUrl.isBlank()) {
            LOGGER.error("Environment variable 'MATRIX_SERVER_URL' not set. Exiting...");
            return -1;
        }

        String matrixSecretName = System.getenv().getOrDefault("MATRIX_SECRET", "matrix-synapse-token");
        String matrixAccessTokenKey = System.getenv().getOrDefault("MATRIX_ACCESS_TOKEN_KEY", "access_token");
        String matrixServerName = System.getenv().getOrDefault("MATRIX_SERVER_NAME", "globex");

        String matrixClientSecret = System.getenv().getOrDefault("CLIENT_SECRET", "client-matrix");

        Secret matrixSecret = client.secrets().inNamespace(matrixNamespace).withName(matrixSecretName).get();
        if (matrixSecret == null) {
            LOGGER.error("Secret " + matrixSecretName + " not found in namespace " + matrixNamespace);
            return -1;
        }

        String accessToken = new String(Base64.getDecoder().decode(matrixSecret.getData().get(matrixAccessTokenKey)));

        String secretData = """
                matrix.access.token=%s
                matrix.server.url=%s
                matrix.server=%s
                """.formatted(accessToken, matrixServerUrl, matrixServerName);

        Secret newSecret = new SecretBuilder().withNewMetadata().withName(matrixClientSecret).endMetadata()
                .addToData("matrix.properties", Base64.getEncoder().encodeToString(secretData.getBytes())).build();
        client.secrets().inNamespace(namespace).resource(newSecret).createOrReplace();

        LOGGER.info("Secret " + matrixClientSecret + " created in namespace " + namespace + ". Exiting.");

        return 0;
    }
}
