package hlavja;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import hlavja.dto.UserLogoutDTO;
import hlavja.entity.User;
import org.apache.http.HttpStatus;

import java.time.ZonedDateTime;
import java.util.Map;

/**
 * Handler for requests to Lambda function.
 */
public class Logout implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    static LambdaLogger logger;
    static DynamoDBMapper dynamoDBMapper = new DynamoDBMapper(AmazonDynamoDBClientBuilder.standard().build(), new DynamoDBMapperConfig.Builder()
            .withTableNameOverride(DynamoDBMapperConfig.TableNameOverride
                    .withTableNameReplacement("User"))
            .build());
    static APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
    static UserLogoutDTO logoutUser;
    static ObjectMapper mapper = new ObjectMapper();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(final APIGatewayProxyRequestEvent input, final Context context) {
        logger = context.getLogger();
        logoutUser = jsonAsObject(input.getBody(), UserLogoutDTO.class);
        User user = dynamoDBMapper.load(User.class, logoutUser.getEmail());
        if (logoutUser.getEmail() != null && user == null) {
            return response.withStatusCode(HttpStatus.SC_NOT_FOUND);
        }
        updateUser(user);
        dynamoDBMapper.save(user);
        return response.withStatusCode(HttpStatus.SC_OK);
    }

    private void updateUser(User user) {
        user.setLoggedIn(false);
        if (!user.getLogins().isEmpty()) {
            int loginNumber = 1;
            for(Map.Entry<String, String> entry : user.getLogins().entrySet()) {
                String key = entry.getKey();
                if (key.contains("login")) {
                    int number = Integer.parseInt(key.replace("login", ""));
                    if (number > loginNumber) {
                        loginNumber = number;
                    }
                }
            }
            user.addLogout("logout" + loginNumber, ZonedDateTime.now().toString());
        }
    }


    public <T> T jsonAsObject(String json, Class<T> clazz) {
        try {
            return mapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return null;
        }
    }
}
