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
import hlavja.dto.UserRegistrationDto;
import hlavja.entity.User;
import org.apache.http.HttpStatus;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.*;

import java.nio.ByteBuffer;
import java.util.Base64;


/**
 * Handler for requests to Lambda function.
 */
public class Register implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    static LambdaLogger logger;
    static DynamoDBMapper dynamoDBMapper = new DynamoDBMapper(AmazonDynamoDBClientBuilder.standard().build(), new DynamoDBMapperConfig.Builder()
            .withTableNameOverride(DynamoDBMapperConfig.TableNameOverride
                    .withTableNameReplacement("User"))
            .build());
    static APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
    static UserRegistrationDto newUser;
    static User user = new User();
    static ObjectMapper mapper = new ObjectMapper();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(final APIGatewayProxyRequestEvent input, final Context context) {
        logger = context.getLogger();
        newUser = jsonAsObject(input.getBody(), UserRegistrationDto.class);
        if (newUser.getEmail() != null && dynamoDBMapper.load(User.class, newUser.getEmail()) != null) {
            return response.withStatusCode(HttpStatus.SC_CONFLICT);
        }
        boolean facePresent = checkFace(newUser.getImage());
        if (facePresent) {
            logger.log(objectAsJson(newUser));
            mapUser();
            dynamoDBMapper.save(user);
            return response.withStatusCode(HttpStatus.SC_OK).withBody(objectAsJson(newUser));
        } else {
            return response.withStatusCode(HttpStatus.SC_NOT_FOUND).withBody("Face not detected!");
        }
    }

    private boolean checkFace(String userImage) {
        try {
            RekognitionClient rekognitionClient = RekognitionClient.builder().region(Region.EU_CENTRAL_1).build();
            Image image = Image.builder()
                    .bytes(SdkBytes.fromByteBuffer(ByteBuffer.wrap(Base64.getDecoder().decode(userImage))))
                    .build();

            DetectFacesRequest detectFacesRequest = DetectFacesRequest.builder().attributes(Attribute.DEFAULT).image(image).build();

            DetectFacesResponse detectFacesResponse = rekognitionClient.detectFaces(detectFacesRequest);
            rekognitionClient.close();
            return detectFacesResponse.faceDetails().size() == 1;
        } catch(RekognitionException e) {
            logger.log(e.toString());
            return false;
        }
    }

    private void mapUser() {
        user.setEmail(newUser.getEmail());
        user.setFirstName(newUser.getFirstName());
        user.setLastName(newUser.getLastName());
        user.setImage(newUser.getImage());
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
