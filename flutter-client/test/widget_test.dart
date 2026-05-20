// This is a basic Flutter widget test.
//
// To perform an interaction with a widget in your test, use the WidgetTester
// utility in the flutter_test package. For example, you can send tap and scroll
// gestures. You can also use WidgetTester to find child widgets in the widget
// tree, read text, and verify that the values of widget properties are correct.

import 'package:flutter_test/flutter_test.dart';

import 'package:enterprise_im_flutter_client/main.dart';

void main() {
  testWidgets('Enterprise IM mobile renders', (WidgetTester tester) async {
    await tester.pumpWidget(const EnterpriseImApp());

    expect(find.text('企业 IM'), findsWidgets);
    expect(find.text('Web 客户端'), findsOneWidget);
    expect(find.text('音视频：未检测 / none'), findsOneWidget);
  });
}
