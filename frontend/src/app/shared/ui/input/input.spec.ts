import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Input } from './input';

@Component({
  standalone: true,
  imports: [Input, ReactiveFormsModule],
  template: `
    <app-input [label]="label" [id]="id" [type]="type" [control]="control" />
  `,
})
class TestHostComponent {
  label = 'Email';
  id = 'email';
  type: 'text' | 'password' | 'email' = 'email';
  control = new FormControl('');
}

describe('Input', () => {
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
    expect(label.textContent).toContain('Email');
  });

  it('should bind to form control', () => {
    const input = fixture.nativeElement.querySelector('input');
    input.value = 'test@example.com';
    input.dispatchEvent(new Event('input'));
    expect(host.control.value).toBe('test@example.com');
  });

  it('should use correct input type', () => {
    const input = fixture.nativeElement.querySelector('input');
    expect(input.type).toBe('email');
  });
});
