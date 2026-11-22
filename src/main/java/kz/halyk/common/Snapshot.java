package kz.halyk.common;

import java.util.List;

// Өз generic класымыз: есеп деректерінің өзгермейтін көшірмесі.
public final class Snapshot<T> {
  private final List<T> items;

  public Snapshot(List<T> items) {
    this.items = List.copyOf(items);
  }

  public List<T> items() {
    return items;
  }
}
