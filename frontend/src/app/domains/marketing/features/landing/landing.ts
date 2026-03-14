import { Component, HostListener, ElementRef, ViewChild } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-landing',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './landing.html',
})
export class Landing {
  @ViewChild('menuButton') menuButton?: ElementRef;
  @ViewChild('mobileMenu') mobileMenu?: ElementRef;

  protected isMenuOpen = false;

  protected readonly navLinks = [
    { label: 'Features', route: '#features', style: 'text' },
    { label: 'Wait List', route: '#waitList', style: 'button' },
  ];

  protected readonly features = [
    {
      title: 'End-to-End Encryption',
      desc: 'Your data belongs to you. Every message is encrypted before it leaves your device.',
      iconPath:
        'M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z',
    },
    {
      title: 'Lightning Fast',
      desc: 'Built on a distributed global network to ensure your messages arrive instantly.',
      iconPath: 'M13 10V3L4 14h7v7l9-11h-7z',
    },
    {
      title: 'Smart Filtering',
      desc: 'AI-driven noise cancellation ensures you only see notifications that matter.',
      iconPath:
        'M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9',
    },
  ];

  @HostListener('document:click', ['$event'])
  handleOutsideClick(event: Event) {
    if (!this.isMenuOpen) return;

    const target = event.target as Node;

    const clickedOutsideMenu = this.mobileMenu && !this.mobileMenu.nativeElement.contains(target);
    const clickedOutsideButton = this.menuButton && !this.menuButton.nativeElement.contains(target);

    if (clickedOutsideMenu && clickedOutsideButton) {
      this.isMenuOpen = false;
    }
  }
}
