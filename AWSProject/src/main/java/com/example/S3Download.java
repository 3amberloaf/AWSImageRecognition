import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.core.sync.ResponseTransformer;

import java.nio.file.Paths;

public class S3Download {
    public static void main(String[] args) {
        S3Client s3 = S3Client.builder().build();
        
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket("njit-cs-643")
                .key("2.jpg") // Change to an image key you want to download
                .build();
        
        s3.getObject(request, ResponseTransformer.toFile(Paths.get("downloaded-image.jpg")));
        
        System.out.println("Image downloaded successfully!");
    }
}
