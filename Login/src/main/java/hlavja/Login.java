package hlavja;

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
import java.util.Base64;


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
    static RekognitionClient rekognitionClient = RekognitionClient.builder()
            .region(Region.EU_CENTRAL_1)
            .build();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(final APIGatewayProxyRequestEvent input, final Context context) {
        logger = context.getLogger();
        loginUser = jsonAsObject(input.getBody(), UserLoginDTO.class);
        User user = dynamoDBMapper.load(User.class, loginUser.getEmail());
        if (loginUser.getEmail() != null && user == null) {
            return response.withStatusCode(HttpStatus.SC_NOT_FOUND);
        }
        boolean approved = compareFaces(user);
        if (approved) {
            return response.withStatusCode(HttpStatus.SC_OK).withBody(objectAsJson(loginUser));
        } else {
            return response.withStatusCode(HttpStatus.SC_UNAUTHORIZED).withBody(objectAsJson(loginUser));
        }
    }

    private boolean compareFaces(User targetUser) {
        try {
            Image souImage = Image.builder()
                    .bytes(SdkBytes.fromByteBuffer(ByteBuffer.wrap(Base64.getDecoder().decode(loginUser.getImage()))))
                    .build();

            Image tarImage = Image.builder()
                    .bytes(SdkBytes.fromByteBuffer(ByteBuffer.wrap(Base64.getDecoder().decode(targetUser.getImage()))))
                    .build();

            CompareFacesRequest facesRequest = CompareFacesRequest.builder()
                    .sourceImage(souImage)
                    .targetImage(tarImage)
                    .similarityThreshold(70F)
                    .build();
            // Compare the two images.
            CompareFacesResponse compareFacesResult = rekognitionClient.compareFaces(facesRequest);
            rekognitionClient.close();
            return compareFacesResult.faceMatches().size() > 0;
        } catch(RekognitionException e) {
            logger.log(e.toString());
            return false;
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