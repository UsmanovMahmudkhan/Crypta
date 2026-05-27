import 'dart:convert';

import 'package:http/http.dart' as http;

class CryptaApiClient {
  CryptaApiClient({
    required String baseUrl,
    required this.bootstrapToken,
    http.Client? httpClient,
  })  : baseUri = Uri.parse(baseUrl.replaceFirst(RegExp(r'/$'), '')),
        _httpClient = httpClient ?? http.Client();

  final Uri baseUri;
  final String bootstrapToken;
  final http.Client _httpClient;
  String? bearerToken;

  Future<SessionResult> issueBootstrapSession({
    required String userId,
    required String deviceId,
  }) async {
    final response = await _post(
      '/api/v1/bootstrap/sessions',
      headers: {'X-Bootstrap-Token': bootstrapToken},
      body: {'userId': userId, 'deviceId': deviceId},
    );
    bearerToken = response['token'] as String?;
    return SessionResult(
      userId: response['userId'] as String,
      deviceId: response['deviceId'] as String,
      token: bearerToken ?? '',
      expiresAt: response['expiresAt'] as String,
    );
  }

  Future<List<Map<String, Object?>>> fetchInbox({
    required String deviceId,
    int limit = 50,
  }) async {
    final uri = _uri('/api/v1/messages/inbox', {
      'deviceId': deviceId,
      'limit': '$limit',
    });
    final response = await _request(() => _httpClient.get(uri, headers: _authHeaders()));
    final decoded = jsonDecode(response.body);
    if (decoded is! List) {
      throw const CryptaApiException('Inbox response was not a list');
    }
    return decoded.cast<Map>().map((item) => item.cast<String, Object?>()).toList();
  }

  Future<String> submitDirectMessage({
    required String organizationId,
    required String senderUserId,
    required String senderDeviceId,
    required String recipientUserId,
    required String recipientDeviceId,
    required String ciphertextBase64,
    required String ciphertextSha256,
    required Map<String, Object> cryptoMetadata,
  }) async {
    final response = await _post(
      '/api/v1/messages/direct',
      body: {
        'organizationId': organizationId,
        'senderUserId': senderUserId,
        'senderDeviceId': senderDeviceId,
        'recipientUserId': recipientUserId,
        'recipientDeviceId': recipientDeviceId,
        'messageKind': 'DIRECT',
        'ciphertextBase64': ciphertextBase64,
        'ciphertextSha256': ciphertextSha256,
        'cryptoMetadata': cryptoMetadata,
      },
    );
    return response['id'] as String;
  }

  Future<void> recordReceipt({
    required String messageId,
    required String deviceId,
    String receiptType = 'READ',
  }) async {
    await _post(
      '/api/v1/messages/receipts',
      body: {'messageId': messageId, 'deviceId': deviceId, 'receiptType': receiptType},
    );
  }

  Future<String> createAttachmentMetadata({
    required String roomId,
    required String objectKey,
    required String ciphertextSha256,
    required int ciphertextBytes,
    required Map<String, Object> cryptoMetadata,
  }) async {
    final response = await _post(
      '/api/v1/attachments',
      body: {
        'roomId': roomId,
        'objectKey': objectKey,
        'ciphertextSha256': ciphertextSha256,
        'ciphertextBytes': ciphertextBytes,
        'cryptoMetadata': cryptoMetadata,
      },
    );
    return response['id'] as String;
  }

  Future<Map<String, Object?>> downloadAttachmentMetadata(String attachmentId) async {
    final response = await _request(
      () => _httpClient.get(_uri('/api/v1/attachments/$attachmentId/download'), headers: _authHeaders()),
    );
    return (jsonDecode(response.body) as Map).cast<String, Object?>();
  }

  Future<Map<String, Object?>> verifierStatus() async {
    final response = await _request(
      () => _httpClient.get(_uri('/api/v1/admin/security/verifier'), headers: _authHeaders()),
    );
    return (jsonDecode(response.body) as Map).cast<String, Object?>();
  }

  Future<Map<String, Object?>> _post(
    String path, {
    Map<String, String>? headers,
    required Map<String, Object?> body,
  }) async {
    final response = await _request(
      () => _httpClient.post(
        _uri(path),
        headers: {..._authHeaders(), ...?headers},
        body: jsonEncode(body),
      ),
    );
    if (response.body.isEmpty) {
      return const {};
    }
    return (jsonDecode(response.body) as Map).cast<String, Object?>();
  }

  Future<http.Response> _request(Future<http.Response> Function() request) async {
    final response = await request();
    if (response.statusCode >= 200 && response.statusCode < 300) {
      return response;
    }
    String message = 'Crypta request failed';
    String? requestId = response.headers['x-request-id'];
    try {
      final decoded = jsonDecode(response.body);
      if (decoded is Map) {
        message = decoded['message']?.toString() ?? message;
        requestId = decoded['requestId']?.toString() ?? requestId;
      }
    } catch (_) {
      if (response.body.isNotEmpty) {
        message = response.body;
      }
    }
    throw CryptaApiException(message, statusCode: response.statusCode, requestId: requestId);
  }

  Map<String, String> _authHeaders() {
    return {
      'Content-Type': 'application/json',
      if (bearerToken != null && bearerToken!.isNotEmpty) 'Authorization': 'Bearer $bearerToken',
    };
  }

  Uri _uri(String path, [Map<String, String>? query]) {
    return baseUri.replace(path: path, queryParameters: query);
  }
}

class SessionResult {
  const SessionResult({
    required this.userId,
    required this.deviceId,
    required this.token,
    required this.expiresAt,
  });

  final String userId;
  final String deviceId;
  final String token;
  final String expiresAt;
}

class CryptaApiException implements Exception {
  const CryptaApiException(this.message, {this.statusCode, this.requestId});

  final String message;
  final int? statusCode;
  final String? requestId;

  @override
  String toString() {
    final suffix = requestId == null ? '' : ' (request $requestId)';
    return '$message$suffix';
  }
}
