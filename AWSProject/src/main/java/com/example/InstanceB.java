package com.example;

import java.io.File;
import java.nio.file.Paths;
import java.util.List;

import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.DetectTextRequest;
import software.amazon.awssdk.services.rekognition.model.DetectTextResponse;
import software.amazon.awssdk.services.rekognition.model.Image;
import software.amazon.awssdk.services.rekognition.model.S3Object;
import software.amazon.awssdk.services.rekognition.model.TextDetection;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

public class InstanceB {

    public static void main(String[] args) {
        // Initialize SQS client
        SqsClient sqsClient = SqsClient.builder().build();
        
        // Initialize S3 and Rekognition clients
        S3Client s3Client = S3Client.builder().build();
        RekognitionClient rekognitionClient = RekognitionClient.builder().build();

        String sqsQueueUrl = "https://sqs.us-east-1.amazonaws.com/160010033088/AWSImageRecognitionQueue"; 
        String s3BucketName = "njit-cs-643";  // S3 bucket where the images are stored

        boolean processing = true;
        while (processing) {
            // Receive messages from SQS queue
            ReceiveMessageRequest receiveMessageRequest = ReceiveMessageRequest.builder()
                    .queueUrl(sqsQueueUrl)
                    .maxNumberOfMessages(10)
                    .waitTimeSeconds(20)  // Long polling to avoid constant polling
                    .build();

            List<Message> messages = sqsClient.receiveMessage(receiveMessageRequest).messages();

            for (Message message : messages) {
                String imageKey = message.body();

                if (imageKey.equals("-1")) {
                    // -1 is the signal from Instance A that all images are processed
                    processing = false;
                    System.out.println("All images processed. Exiting.");
                    break;
                }

                // Download image from S3
                File localImageFile = new File("/tmp/" + imageKey);  // Save the image locally in /tmp/
                GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                        .bucket(s3BucketName)
                        .key(imageKey)
                        .build();
                
                s3Client.getObject(getObjectRequest, Paths.get(localImageFile.getPath()));

                // Use Rekognition to perform text detection
                DetectTextRequest detectTextRequest = DetectTextRequest.builder()
                        .image(Image.builder()
                                .s3Object(S3Object.builder()
                                        .bucket(s3BucketName)
                                        .name(imageKey)
                                        .build())
                                .build())
                        .build();

                DetectTextResponse detectTextResponse = rekognitionClient.detectText(detectTextRequest);
                List<TextDetection> textDetections = detectTextResponse.textDetections();

                System.out.println("Detected text in image " + imageKey + ":");
                for (TextDetection text : textDetections) {
                    System.out.println("Detected: " + text.detectedText());
                }

                // After processing, delete the message from the queue
                DeleteMessageRequest deleteMessageRequest = DeleteMessageRequest.builder()
                        .queueUrl(sqsQueueUrl)
                        .receiptHandle(message.receiptHandle())
                        .build();
                sqsClient.deleteMessage(deleteMessageRequest);
                System.out.println("Deleted message for image: " + imageKey);
            }
        }
    }
}
