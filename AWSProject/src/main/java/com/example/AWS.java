package com.example;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.core.sync.ResponseTransformer;

import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.*;

import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;

import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

public class AWS {
    private static final String BUCKET_NAME = "njit-cs-643";
    private static final String SQS_QUEUE_URL = "https://sqs.us-east-1.amazonaws.com/160010033088/AWSImageRecognitionQueue";

    private static S3Client s3Client;
    private static RekognitionClient rekognitionClient;
    private static SqsClient sqsClient;

    public static void main(String[] args) {
        initializeClients();
        processImages();
        processTextDetection();
    }

    private static void initializeClients() {
        s3Client = S3Client.builder().build();
        rekognitionClient = RekognitionClient.builder().build();
        sqsClient = SqsClient.builder().build();
    }

    private static void processImages() {
        // Loop through the 10 images
        for (int i = 1; i <= 10; i++) {
            String imageKey = i + ".jpg";
            String imagePath = "/tmp/" + imageKey;  // Download to a temporary directory

            // Download the image from S3
            s3Client.getObject(GetObjectRequest.builder()
                            .bucket(BUCKET_NAME)
                            .key(imageKey)
                            .build(),
                    ResponseTransformer.toFile(Paths.get(imagePath))
            );

            // Perform object detection using Rekognition
            Image image = Image.builder()
                    .s3Object(S3Object.builder()
                            .bucket(BUCKET_NAME)
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
                        .queueUrl(SQS_QUEUE_URL)
                        .messageBody(imageKey)
                        .build();

                sqsClient.sendMessage(sendMsgRequest);
                System.out.println("Car detected in image: " + imageKey + ". Sent to SQS.");
            } else {
                System.out.println("No car detected in image: " + imageKey);
            }
        }

        // Signal that processing is done
        sqsClient.sendMessage(SendMessageRequest.builder()
                .queueUrl(SQS_QUEUE_URL)
                .messageBody("-1")
                .build());
    }

    private static void processTextDetection() {
        boolean processing = true;
        while (processing) {
            // Receive messages from SQS queue
            ReceiveMessageRequest receiveMessageRequest = ReceiveMessageRequest.builder()
                    .queueUrl(SQS_QUEUE_URL)
                    .maxNumberOfMessages(10)
                    .waitTimeSeconds(20)  // Long polling
                    .build();

            List<Message> messages = sqsClient.receiveMessage(receiveMessageRequest).messages();

            for (Message message : messages) {
                String imageKey = message.body();

                if (imageKey.equals("-1")) {
                    processing = false;
                    System.out.println("All images processed. Exiting.");
                    break;
                }

                // Download image from S3
                File localImageFile = new File("/tmp/" + imageKey);
                GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                        .bucket(BUCKET_NAME)
                        .key(imageKey)
                        .build();

                s3Client.getObject(getObjectRequest, Paths.get(localImageFile.getPath()));

                // Use Rekognition for text detection
                DetectTextRequest detectTextRequest = DetectTextRequest.builder()
                        .image(Image.builder()
                                .s3Object(S3Object.builder()
                                        .bucket(BUCKET_NAME)
                                        .name(imageKey)
                                        .build())
                                .build())
                        .build();

                DetectTextResponse detectTextResponse = rekognitionClient.detectText(detectTextRequest);
                List<TextDetection> textDetections = detectTextResponse.textDetections();

                // Log detected text
                if (!textDetections.isEmpty()) {
                    try (FileWriter writer = new FileWriter("/home/ec2-user/output.txt", true)) {  // Append mode
                        writer.write("Image Key (with car detected): " + imageKey + "\n");
                        writer.write("Detected text:\n");
                        for (TextDetection text : textDetections) {
                            writer.write("Detected: " + text.detectedText() + "\n");
                            System.out.println("Detected: " + text.detectedText());
                        }
                        writer.write("\n");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                // Delete the message from the queue
                DeleteMessageRequest deleteMessageRequest = DeleteMessageRequest.builder()
                        .queueUrl(SQS_QUEUE_URL)
                        .receiptHandle(message.receiptHandle())
                        .build();
                sqsClient.deleteMessage(deleteMessageRequest);
                System.out.println("Deleted message for image: " + imageKey);
            }
        }
    }
}
