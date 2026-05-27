import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sovereign_comm_mobile/main.dart';
import 'package:sovereign_comm_mobile/src/app/mission_room_controller.dart';

void main() {
  testWidgets('renders stable offline demo shell', (tester) async {
    await tester.pumpWidget(SovereignCommApp(controller: MissionRoomController()));
    await tester.pump();

    expect(find.text('Sovereign Comm'), findsOneWidget);
    expect(find.text('Executive Ops'), findsWidgets);
    expect(find.byKey(const Key('message-composer')), findsOneWidget);
    expect(find.textContaining('Offline demo ready'), findsOneWidget);
  });

  testWidgets('sends deterministic demo ciphertext', (tester) async {
    final controller = MissionRoomController();
    await tester.pumpWidget(SovereignCommApp(controller: controller));
    await tester.pump();

    await tester.enterText(find.byKey(const Key('message-composer')), 'Meet at 09:00');
    await tester.tap(find.byKey(const Key('send-button')));
    await tester.pumpAndSettle();

    expect(controller.sendState, SendState.sent);
    expect(controller.messages.last.body, contains('Sent as deterministic demo ciphertext'));
    expect(controller.messages.last.status, 'offline queued');
  });

  testWidgets('lockdown disables sending and shows blocked state', (tester) async {
    await tester.pumpWidget(SovereignCommApp(controller: MissionRoomController()));
    await tester.pump();

    await tester.tap(find.byKey(const Key('lockdown-button')));
    await tester.pumpAndSettle();

    expect(find.textContaining('Demo lockdown active'), findsWidgets);
    expect(tester.widget<IconButton>(find.byKey(const Key('send-button'))).onPressed, isNull);
  });

  testWidgets('advanced actions have explicit demo outcomes', (tester) async {
    final controller = MissionRoomController();
    await tester.pumpWidget(SovereignCommApp(controller: controller));
    await tester.pump();

    await tester.tap(find.byKey(const Key('verify-device-button')));
    await tester.pumpAndSettle();
    expect(find.textContaining('Device marked verified'), findsWidgets);

    await tester.tap(find.byKey(const Key('passkey-button')));
    await tester.pumpAndSettle();
    expect(find.textContaining('authenticator verification is demo-only'), findsWidgets);

    await tester.tap(find.byKey(const Key('attach-button')));
    await tester.pumpAndSettle();
    expect(controller.messages.last.sender, contains('encrypted attachment'));
    expect(controller.statusText, 'Encrypted attachment metadata prepared');
  });
}
