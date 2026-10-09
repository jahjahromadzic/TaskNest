import { Component, ElementRef, HostListener, Input, forwardRef, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { Check, ChevronDown } from 'lucide';
import { Icon } from '../icon/icon';

export interface SelectOption {
  value: string;
  label: string;
  group?: string;
}

function normalize(text: string): string {
  return text
    .toLowerCase()
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .replace(/đ/g, 'd');
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
  @Input() searchable = false;
  @Input() searchPlaceholder = 'Search...';
  @Input() noMatchesText = 'No matches';

  readonly value = signal<string | null>(null);
  readonly open = signal(false);
  readonly activeIndex = signal(-1);
  readonly disabled = signal(false);
  readonly query = signal('');

  private onChange: (value: string) => void = () => undefined;
  private onTouched: () => void = () => undefined;

  constructor(private host: ElementRef<HTMLElement>) {}

  get listId(): string {
    return `${this.inputId}-list`;
  }

  get selectedLabel(): string | undefined {
    return this.options.find((option) => option.value === this.value())?.label;
  }

  get visibleOptions(): SelectOption[] {
    const query = normalize(this.query().trim());
    if (!query) {
      return this.options;
    }
    return this.options.filter(
      (option) => normalize(option.label).includes(query) || normalize(option.group ?? '').includes(query),
    );
  }

  startsGroup(options: SelectOption[], index: number): boolean {
    const group = options[index].group;
    return !!group && (index === 0 || options[index - 1].group !== group);
  }

  search(text: string): void {
    this.query.set(text);
    this.setActive(this.visibleOptions.length > 0 ? 0 : -1);
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
    this.onListKeydown(event);
  }

  onSearchKeydown(event: KeyboardEvent): void {
    if (event.key === ' ' || event.key === 'Home' || event.key === 'End') {
      return;
    }
    this.onListKeydown(event);
  }

  private onListKeydown(event: KeyboardEvent): void {
    const options = this.visibleOptions;
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
        this.setActive(options.length - 1);
        break;
      case 'Enter':
      case ' ':
        event.preventDefault();
        if (options[this.activeIndex()]) {
          this.choose(options[this.activeIndex()]);
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
    this.query.set('');
    this.open.set(true);
    this.setActive(selected >= 0 ? selected : 0);
    if (this.searchable) {
      setTimeout(() => this.host.nativeElement.querySelector<HTMLInputElement>('input[type=search]')?.focus());
    }
  }

  private close(focusButton = false): void {
    if (!this.open()) {
      return;
    }
    this.open.set(false);
    this.query.set('');
    this.onTouched();
    if (focusButton) {
      this.host.nativeElement.querySelector<HTMLButtonElement>('button')?.focus();
    }
  }

  private moveActive(step: number): void {
    const count = this.visibleOptions.length;
    if (count === 0) {
      return;
    }
    this.setActive((this.activeIndex() + step + count) % count);
  }

  private setActive(index: number): void {
    this.activeIndex.set(index);
    queueMicrotask(() =>
      this.host.nativeElement.querySelector(`#${this.optionId(index)}`)?.scrollIntoView?.({ block: 'nearest' }),
    );
  }
}
