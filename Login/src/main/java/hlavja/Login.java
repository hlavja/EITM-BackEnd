package hlavja;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
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
import hlavja.dto.UserLoginDTO;
import hlavja.entity.User;
import org.apache.http.HttpStatus;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.*;

import java.nio.ByteBuffer;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;


/**
 * Handler for requests to Lambda function.
 */
public class Login implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    static LambdaLogger logger;
    static DynamoDBMapper dynamoDBMapper = new DynamoDBMapper(AmazonDynamoDBClientBuilder.standard().build(), new DynamoDBMapperConfig.Builder()
            .withTableNameOverride(DynamoDBMapperConfig.TableNameOverride
                    .withTableNameReplacement("User"))
            .build());
    static APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
    static UserLoginDTO loginUser;
    static ObjectMapper mapper = new ObjectMapper();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(final APIGatewayProxyRequestEvent input, final Context context) {
        logger = context.getLogger();
        loginUser = jsonAsObject(input.getBody(), UserLoginDTO.class);
        //User user = dynamoDBMapper.load(User.class, loginUser.getEmail());
        if (loginUser.getImage() == null || loginUser.getImage().isBlank()) {
            return response.withStatusCode(HttpStatus.SC_NOT_FOUND);
        }
        //logger.log("Logging in user " + user.getEmail());
        List<User> userList = dynamoDBMapper.scan(User.class, new DynamoDBScanExpression());
        userList.forEach(user -> logger.log("Found: " + user.getEmail()));
        User user = compareFaces(userList);
        if (user != null) {
            logger.log("Logging in as: " + user.getEmail());
            updateUser(user);
            dynamoDBMapper.save(user);
            return response.withStatusCode(HttpStatus.SC_OK).withBody(objectAsJson(user));
        } else {
            return response.withStatusCode(HttpStatus.SC_UNAUTHORIZED).withBody(objectAsJson(loginUser));
        }
    }

    private void updateUser(User user) {
        user.setLoggedIn(true);
        if (user.getLogins() != null && !user.getLogins().isEmpty()) {
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
            String logout = user.getLogins().get("logout"+loginNumber);
            if (logout == null) {
                user.addLogin("logout" + loginNumber, ZonedDateTime.now().minusHours(1).toString());
            }
            loginNumber = loginNumber + 1;
            user.addLogin("login" + loginNumber, ZonedDateTime.now().toString());
        } else {
            user.addLogin("login1", ZonedDateTime.now().toString());
        }
    }

    private User compareFaces(List<User> usersList) {
        try {
            for (User user : usersList) {
                RekognitionClient rekognitionClient = RekognitionClient.builder().region(Region.EU_CENTRAL_1).build();

                Image souImage = Image.builder()
                        .bytes(SdkBytes.fromByteBuffer(ByteBuffer.wrap(Base64.getDecoder().decode(loginUser.getImage()))))
                        .build();

                Image tarImage = Image.builder()
                        .bytes(SdkBytes.fromByteBuffer(ByteBuffer.wrap(Base64.getDecoder().decode(user.getImage()))))
                        .build();

                CompareFacesRequest facesRequest = CompareFacesRequest.builder()
                        .sourceImage(souImage)
                        .targetImage(tarImage)
                        .similarityThreshold(70F)
                        .build();

                // Compare the two images.
                CompareFacesResponse compareFacesResult = rekognitionClient.compareFaces(facesRequest);
                rekognitionClient.close();
                if (compareFacesResult.faceMatches().size() > 0) {
                    return user;
                }
            }
            return null;
        } catch(RekognitionException e) {
            logger.log(e.toString());
            return null;
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

    public String objectAsJson(Object object) {
        try {
            return mapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return null;
        }
    }
}
