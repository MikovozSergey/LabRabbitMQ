package by.bsu.web.rabbitmq;

import by.bsu.web.SendEmailTLS;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.QueueingConsumer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class Consumer implements Runnable {
	private Channel channel;
	private QueueingConsumer consumer;

	public Consumer(String host, String queueName) {
		ConnectionFactory factory = new ConnectionFactory();
		try {
			factory.setUri(host);

			factory.setConnectionTimeout(300000);
			Connection connection = factory.newConnection();
			this.channel = connection.createChannel();
			this.channel.queueDeclare(queueName, true, false, false, null);

			this.consumer = new QueueingConsumer(channel);
			this.channel.basicConsume(queueName, false, consumer);
		} catch (Exception e) {
			System.out.println(e.toString());
		}
	}

	@Override
	public void run() {
		while (true) {
			try {
				QueueingConsumer.Delivery delivery = consumer.nextDelivery();

				if (delivery != null) {
					try {
						String message = new String(delivery.getBody(), StandardCharsets.UTF_8);

						System.out.println("Message consumed: " + message);
						String awsResp = sendMessageToAws(message);
						System.out.println("Responce received :" + awsResp);
						SendEmailTLS sender = new SendEmailTLS();
						sender.sendEmail(awsResp);
						// send Acknowledgement that message got consumed successfully
						channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
					} catch (Exception e) {
						e.printStackTrace();
						// If message got cannot be consumed - notify queue(otherwise message will get
						// lost)
						channel.basicReject(delivery.getEnvelope().getDeliveryTag(), true);
					}
				}
			} catch (Exception e1) {
				e1.printStackTrace();
			}
		}
	}

	private String sendMessageToAws(String message) throws IOException {
		URL url = new URL("https://5n7wcvzqu3.execute-api.us-east-2.amazonaws.com/modifyTextStage");
		HttpURLConnection con = (HttpURLConnection) url.openConnection();
		con.setRequestMethod("POST");
		con.setRequestProperty("Content-Type", "application/json; utf-8");
		con.setRequestProperty("Accept", "application/json");
		con.setDoOutput(true);
		String jsonInputString = "{\"message\":\"" + message + "\"}";
		try (OutputStream os = con.getOutputStream()) {
			byte[] input = jsonInputString.getBytes("utf-8");
			os.write(input, 0, input.length);
		}
		try (BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream(), "utf-8"))) {
			StringBuilder response = new StringBuilder();
			String responseLine = null;
			while ((responseLine = br.readLine()) != null) {
				response.append(responseLine.trim());
			}
			return response.toString();
		}
	}
}
