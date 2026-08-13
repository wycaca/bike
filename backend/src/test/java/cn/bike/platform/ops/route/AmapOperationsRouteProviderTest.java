package cn.bike.platform.ops.route;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AmapOperationsRouteProviderTest {

    @Test
    void 高德五零三响应应有限重试后成功() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var uri = URI.create("https://restapi.amap.com/v3/distance?key=secret");
        server.expect(once(), requestTo(uri)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(once(), requestTo(uri)).andRespond(withSuccess("{\"status\":\"1\"}", MediaType.APPLICATION_JSON));
        var provider = new AmapOperationsRouteProvider(
                builder.build(), "secret", uri.toString(), uri.toString(), 2, 0);

        var response = provider.get(uri);

        assertThat(response.path("status").asText()).isEqualTo("1");
        server.verify();
    }
}
