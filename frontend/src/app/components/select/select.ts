import { Component, ElementRef, HostListener, Input, forwardRef, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { Check, ChevronDown } from 'lucide';
import { Icon } from '../icon/icon';

export interface SelectOption {
  value: string;
  label: string;
}

let nextId = 0;

@Component({
  selector: 'app-select',
  imports: [Icon],
  templateUrl: './select.html',
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => Select), multi: true }],
  host: { class: 'relative block' },
})
export class Select implements ControlValueAccessor {
  protected readonly icons = { Check, ChevronDown };

  @Input() options: SelectOption[] = [];
  @Input() placeholder = 'Select';
  @Input() inputId = `app-select-${nextId++}`;
  @Input() compact = false;

  readonly value = signal<string | null>(null);
  readonly open = signal(false);
  readonly activeIndex = signal(-1);
  readonly disabled = signal(false);

  private onChange: (value: string) => void = () => undefined;
  private onTouched: () => void = () => undefined;

  constructor(private host: ElementRef<HTMLElement>) {}

  get listId(): string {
    return `${this.inputId}-list`;
  }

  get selectedLabel(): string | undefined {
    return this.options.find((option) => option.value === this.value())?.label;
  }

  optionId(index: number): string {
    return `${this.inputId}-option-${index}`;
  }

  toggle(): void {
    if (this.open()) {
      this.close();
    } else {
      this.openList();
    }
  }

  choose(option: SelectOption): void {
    this.close(true);
    if (option.value !== this.value()) {
      this.value.set(option.value);
      this.onChange(option.value);
    }
  }

  onButtonKeydown(event: KeyboardEvent): void {
    if (!this.open()) {
      if (['ArrowDown', 'ArrowUp', 'Enter', ' '].includes(event.key)) {
        event.preventDefault();
        this.openList();
      }
      return;
    }

    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        this.moveActive(1);
        break;
      case 'ArrowUp':
        event.preventDefault();
        this.moveActive(-1);
        break;
      case 'Home':
        event.preventDefault();
        this.setActive(0);
        break;
      case 'End':
        event.preventDefault();
        this.setActive(this.options.length - 1);
        break;
      case 'Enter':
      case ' ':
        event.preventDefault();
        if (this.options[this.activeIndex()]) {
          this.choose(this.options[this.activeIndex()]);
        }
        break;
      case 'Escape':
        event.preventDefault();
        this.close(true);
        break;
      case 'Tab':
        this.close();
        break;
    }
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (this.open() && !this.host.nativeElement.contains(event.target as Node)) {
      this.close();
    }
  }

  writeValue(value: string | null): void {
    this.value.set(value);
  }

  registerOnChange(fn: (value: string) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(disabled: boolean): void {
    this.disabled.set(disabled);
  }

  private openList(): void {
    if (this.disabled() || this.options.length === 0) {
      return;
    }
    const selected = this.options.findIndex((option) => option.value === this.value());
    this.open.set(true);
    this.setActive(selected >= 0 ? selected : 0);
  }

  private close(focusButton = false): void {
    if (!this.open()) {
      return;
    }
    this.open.set(false);
    this.onTouched();
    if (focusButton) {
      this.host.nativeElement.querySelector<HTMLButtonElement>('button')?.focus();
    }
  }

  private moveActive(step: number): void {
    const count = this.options.length;
    this.setActive((this.activeIndex() + step + count) % count);
  }

  private setActive(index: number): void {
    this.activeIndex.set(index);
    queueMicrotask(() =>
      this.host.nativeElement.querySelector(`#${this.optionId(index)}`)?.scrollIntoView?.({ block: 'nearest' }),
    );
  }
}
