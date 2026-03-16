import { Component, inject } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthInput } from './auth-input';

@Component({
  standalone: true,
  imports: [AuthInput, ReactiveFormsModule],
  template: `
    <app-auth-input
      [id]="id"
      [label]="label"
      [type]="type"
      [placeholder]="placeholder"
      [control]="control"
      [errorMessage]="errorMessage"
      [hint]="hint"
    />
  `,
})
class TestHostComponent {
  private fb = inject(NonNullableFormBuilder);

  id = 'email';
  label = 'Email Address';
  type: 'text' | 'password' | 'email' = 'email';
  placeholder = 'you@example.com';
  control: FormControl<string> = this.fb.control('', [Validators.required, Validators.email]);
  errorMessage = 'Email is required';
  hint = '';
}

@Component({
  standalone: true,
  imports: [AuthInput, ReactiveFormsModule],
  template: `
    <app-auth-input
      id="password"
      label="Password"
      type="password"
      placeholder="Min. 8 characters"
      [control]="control"
      errorMessage="Password is required"
      hint="Must be at least 8 characters"
    />
  `,
})
class TestHostWithHintComponent {
  private fb = inject(NonNullableFormBuilder);
  control: FormControl<string> = this.fb.control('validpassword', [Validators.required]);
}

describe('AuthInput', () => {
  let fixture: ComponentFixture<TestHostComponent>;
  let host: TestHostComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TestHostComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(TestHostComponent);
    host = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(host).toBeTruthy();
  });

  it('should render label', () => {
    const label = fixture.nativeElement.querySelector('label');
    expect(label.textContent).toContain('Email Address');
  });

  it('should use correct input type', () => {
    const input = fixture.nativeElement.querySelector('input');
    expect(input.type).toBe('email');
  });

  it('should display placeholder', () => {
    const input = fixture.nativeElement.querySelector('input');
    expect(input.placeholder).toBe('you@example.com');
  });

  it('should bind to form control', () => {
    const input = fixture.nativeElement.querySelector('input');
    input.value = 'test@example.com';
    input.dispatchEvent(new Event('input'));
    expect(host.control.value).toBe('test@example.com');
  });

  it('should show error message when control is invalid and touched', () => {
    host.control.markAsTouched();
    fixture.detectChanges();

    const error = fixture.nativeElement.querySelector('.text-error');
    expect(error.textContent).toContain('Email is required');
  });

  it('should not show error message when control is valid', () => {
    host.control.setValue('valid@email.com');
    host.control.markAsTouched();
    fixture.detectChanges();

    const error = fixture.nativeElement.querySelector('.text-error');
    expect(error).toBeNull();
  });

  it('should show hint when provided and control is valid', async () => {
    const hintFixture = TestBed.createComponent(TestHostWithHintComponent);
    hintFixture.detectChanges();

    const hint = hintFixture.nativeElement.querySelector('p.text-muted');
    expect(hint.textContent).toContain('Must be at least 8 characters');
  });

  it('should apply error styling when invalid and touched', () => {
    host.control.markAsTouched();
    fixture.detectChanges();

    const input = fixture.nativeElement.querySelector('input');
    expect(input.classList).toContain('border-error');
  });
});
