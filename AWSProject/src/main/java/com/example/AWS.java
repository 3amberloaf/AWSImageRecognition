package com.example;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.*;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class AWS {
    private static final String BUCKET_NAME = "njit-cs-643";
    private static final String SQS_QUEUE_URL = "https://sqs.us-east-1.amazonaws.com/160010033088/AWSImageRecognitionQueue";

    private static S3Client s3Client;
    private static RekognitionClient rekognitionClient;
    private static SqsClient sqsClient;

    public static void main(String[] args) {
        initializeClients();

        // Create threads for Instance A and Instance B
        Thread instanceAThread = new Thread(new InstanceA());
        Thread instanceBThread = new Thread(new InstanceB());

        // Start both threads
        instanceAThread.start();
        instanceBThread.start();

        // Wait for both threads to finish
        try {
            instanceAThread.join();
            instanceBThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static void initializeClients() {
        s3Client = S3Client.builder().build();
        rekognitionClient = RekognitionClient.builder().build();
        sqsClient = SqsClient.builder().build();
    }

    // Instance A for detecting cars
    static class InstanceA implements Runnable {
        @Override
        public void run() {
            processImages();
        }

        private void processImages() {
            // Loop through the 10 images
            for (int i = 1; i <= 10; i++) {
                String imageKey = i + ".jpg";
                String imagePath = "/tmp/" + imageKey;  // Download to a temporary directory

                // Delete existing file if it exists
                try {
                    Files.deleteIfExists(Paths.get(imagePath));
                } catch (IOException e) {
                    System.err.println("Error deleting existing file: " + e.getMessage());
                }

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
                    // Include both index and image key in the message body
                    String messageBody = i + "," + imageKey;  // Using comma as a delimiter
                    SendMessageRequest sendMsgRequest = SendMessageRequest.builder()
                            .queueUrl(SQS_QUEUE_URL)
                            .messageBody(messageBody)  // Send index and image key
                            .build();

                    sqsClient.sendMessage(sendMsgRequest);
                    System.out.println("Car detected in image: " + imageKey + ". Sent to SQS with index: " + i);
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
    }

    // Instance B for detecting text
    static class InstanceB implements Runnable {
        @Override
        public void run() {
            processTextDetection();
        }

        private void processTextDetection() {
            boolean processing = true;
            File outputFile = new File("/tmp/output.txt");

            // Create the output file if it doesn't exist
            try {
                if (outputFile.createNewFile()) {
                    System.out.println("Output file created: " + outputFile.getAbsolutePath());
                } else {
                    System.out.println("Output file already exists: " + outputFile.getAbsolutePath());
                }
            } catch (IOException e) {
                System.err.println("Error creating output file: " + e.getMessage());
            }

            while (processing) {
                // Receive messages from SQS queue
                ReceiveMessageRequest receiveMessageRequest = ReceiveMessageRequest.builder()
                        .queueUrl(SQS_QUEUE_URL)
                        .maxNumberOfMessages(10)
                        .waitTimeSeconds(20)  // Long polling
                        .build();

                List<Message> messages = sqsClient.receiveMessage(receiveMessageRequest).messages();

                for (Message message : messages) {
                    String[] messageParts = message.body().split(",");  // Split the message by comma
                    if (messageParts.length != 2) {
                        System.err.println("Invalid message format: " + message.body());
                        continue;  // Skip invalid messages
                    }
                    String index = messageParts[0].trim();  // Extract index
                    String imageKey = messageParts[1].trim();  // Extract image key

                    if (imageKey.equals("-1")) {
                        // Signal that processing is complete
                        processing = false;
                        System.out.println("All images processed. Exiting.");
                        break;
                    }

                    // Delete existing file if it exists
                    String localImagePath = "/tmp/" + imageKey;
                    try {
                        Files.deleteIfExists(Paths.get(localImagePath));
                    } catch (IOException e) {
                        System.err.println("Error deleting existing file: " + e.getMessage());
                    }

                    // Download image from S3
                    GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                            .bucket(BUCKET_NAME)
                            .key(imageKey)
                            .build();

                    s3Client.getObject(getObjectRequest, Paths.get(localImagePath));

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

                    // Check if text is detected and if the corresponding image has a car
                    if (!textDetections.isEmpty()) {
                        // Writing detected text to the output file
                        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile, true))) {  // Append mode
                            writer.write("=== Detected Text for Image Index: " + index + " (Car Detected) ===\n");
                            writer.write("Image Key: " + imageKey + "\n");
                            writer.write("Detected text:\n");
                            for (TextDetection text : textDetections) {
                                writer.write("Detected: " + text.detectedText() + "\n");
                            }
                            writer.write("\n");
                        } catch (IOException e) {
                            System.err.println("Error writing to file: " + e.getMessage());
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
}
