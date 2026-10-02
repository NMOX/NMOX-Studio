import 'dart:async';

class Counter {
  int _value = 0;
  int get value => _value;
  void increment([int by = 1]) => _value += by;
}

Future<void> main() async {
  final counter = Counter()..increment()..increment(2);
  final names = <String>['a', 'b'];
  await Future.delayed(const Duration(milliseconds: 10));
  print('value: ${counter.value}, names: ${names.join(', ')}');
}
