package com.example;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.core.sync.ResponseTransformer;

import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.*;

import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.nio.file.Paths;
import java.util.List;

import javax.swing.plaf.synth.Region;

public class InstanceA {
    public static void main(String[] args) {
        // Initialize S3 and Rekognition clients

        software.amazon.awssdk.regions.Region region = software.amazon.awssdk.regions.Region.US_EAST_1;
        S3Client s3 = S3Client.builder().build();
        RekognitionClient rekognitionClient = RekognitionClient.builder().build();
        SqsClient sqsClient = SqsClient.builder().build();

        String bucketName = "njit-cs-643";
        String sqsQueueUrl = "https://sqs.us-east-1.amazonaws.com/160010033088/AWSImageRecognitionQueue"; 

        // Loop through the 10 images
        for (int i = 1; i <= 10; i++) {
            String imageKey = i + ".jpg";
            String imagePath = "/tmp/" + imageKey;  // Download to a temporary directory

            // Download the image from S3
            s3.getObject(GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(imageKey)
                    .build(),
                    ResponseTransformer.toFile(Paths.get(imagePath))
            );

            // Perform object detection using Rekognition
            Image image = Image.builder()
                    .s3Object(S3Object.builder()
                            .bucket(bucketName)
                            .name(imageKey)
                            .build())
                    .build();

            DetectLabelsRequest detectLabelsRequest = DetectLabelsRequest.builder()
                    .image(image)
                    .minConfidence(90F)
                    .build();

            DetectLabelsResponse labelsResponse = rekognitionClient.detectLabels(detectLabelsRequest);
            List<Label> labels = labelsResponse.labels();

            // Check if a car was detected with confidence > 90%
            boolean carDetected = labels.stream()
                    .anyMatch(label -> label.name().equalsIgnoreCase("Car") && label.confidence() > 90);

            if (carDetected) {
                // Send image index to SQS queue
                SendMessageRequest sendMsgRequest = SendMessageRequest.builder()
                        .queueUrl(sqsQueueUrl)
                        .messageBody(imageKey)
                        .build();

                sqsClient.sendMessage(sendMsgRequest);
                System.out.println("Car detected in image: " + imageKey + ". Sent to SQS.");
            } else {
                System.out.println("No car detected in image: " + imageKey);
            }
        }

        // After processing all images, signal Instance B that processing is done by sending -1 to SQS
        sqsClient.sendMessage(SendMessageRequest.builder()
                .queueUrl(sqsQueueUrl)
                .messageBody("-1")
                .build());
    }
}
