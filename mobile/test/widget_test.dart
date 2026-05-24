import 'package:flutter_test/flutter_test.dart';
import 'package:sovereign_comm_mobile/main.dart';

void main() {
  testWidgets('renders mission room shell', (tester) async {
    await tester.pumpWidget(const SovereignCommApp());

    expect(find.text('Sovereign Comm'), findsOneWidget);
    expect(find.text('Executive Ops'), findsOneWidget);
    expect(find.text('Compose locally encrypted message'), findsOneWidget);
  });
}
