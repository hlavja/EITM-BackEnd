package hlavja;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
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
import hlavja.entity.User;
import org.apache.http.HttpStatus;

import java.util.List;


/**
 * Handler for requests to Lambda function.
 */
public class Dashboard implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    static LambdaLogger logger;
    static DynamoDBMapper dynamoDBMapper = new DynamoDBMapper(AmazonDynamoDBClientBuilder.standard().build(), new DynamoDBMapperConfig.Builder()
            .withTableNameOverride(DynamoDBMapperConfig.TableNameOverride
                    .withTableNameReplacement("User"))
            .build());
    static APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
    static ObjectMapper mapper = new ObjectMapper();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(final APIGatewayProxyRequestEvent input, final Context context) {
        logger = context.getLogger();
        List<User> user = dynamoDBMapper.scan(User.class, new DynamoDBScanExpression());
        if (user == null) {
            return response.withStatusCode(HttpStatus.SC_NOT_FOUND);
        }
        if (!user.isEmpty()) {
            return response.withStatusCode(HttpStatus.SC_OK).withBody(objectAsJson(user));
        } else {
            return response.withStatusCode(HttpStatus.SC_UNAUTHORIZED).withBody("ERROR");
        }
    }

    public String objectAsJson(Object object) {
        try {
            return mapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return null;
        }
    }
}
