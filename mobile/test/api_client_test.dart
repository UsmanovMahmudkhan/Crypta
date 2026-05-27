import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:sovereign_comm_mobile/src/api/crypta_api_client.dart';

void main() {
  test('stores bootstrap bearer token and parses session', () async {
    final client = CryptaApiClient(
      baseUrl: 'http://localhost:8080',
      bootstrapToken: 'bootstrap',
      httpClient: MockClient((request) async {
        expect(request.url.path, '/api/v1/bootstrap/sessions');
        expect(request.headers['X-Bootstrap-Token'], 'bootstrap');
        return http.Response(
          '{"userId":"user-1","deviceId":"device-1","token":"session-token","expiresAt":"2026-05-27T00:00:00Z"}',
          200,
          headers: {'content-type': 'application/json'},
        );
      }),
    );

    final session = await client.issueBootstrapSession(userId: 'user-1', deviceId: 'device-1');

    expect(session.token, 'session-token');
    expect(client.bearerToken, 'session-token');
  });

  test('surfaces normalized backend errors with request id', () async {
    final client = CryptaApiClient(
      baseUrl: 'http://localhost:8080',
      bootstrapToken: 'bootstrap',
      httpClient: MockClient((request) async {
        return http.Response(
          '{"status":400,"error":"Bad Request","message":"ciphertextSha256 must be a SHA-256 value","path":"/api/v1/messages/direct","requestId":"req-1"}',
          400,
          headers: {'content-type': 'application/json', 'x-request-id': 'req-1'},
        );
      }),
    );

    expect(
      client.submitDirectMessage(
        organizationId: 'org',
        senderUserId: 'sender',
        senderDeviceId: 'sender-device',
        recipientUserId: 'recipient',
        recipientDeviceId: 'recipient-device',
        ciphertextBase64: 'ZmFrZQ==',
        ciphertextSha256: 'invalid',
        cryptoMetadata: const {'algorithm': 'DEMO'},
      ),
      throwsA(
        isA<CryptaApiException>()
            .having((error) => error.statusCode, 'statusCode', 400)
            .having((error) => error.requestId, 'requestId', 'req-1')
            .having((error) => error.message, 'message', contains('SHA-256')),
      ),
    );
  });
}
