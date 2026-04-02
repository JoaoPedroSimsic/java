import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { signal } from '@angular/core';

import { Home } from './home';
import { AuthStore } from '@domains/auth';
import { ConversationStore } from '../../data/conversation.store';

describe('Home', () => {
  let component: Home;
  let fixture: ComponentFixture<Home>;
  let mockAuthStore: Partial<AuthStore>;
  let mockConversationStore: Partial<ConversationStore>;

  beforeEach(async () => {
    mockAuthStore = {
      user: signal({ id: '1', email: 'test@example.com', name: 'Test User', emailVerified: true }),
      isAuthenticated: signal(true),
      logout: vi.fn(),
    };

    mockConversationStore = {
      conversations: signal([]),
      isLoading: signal(false),
      searchQuery: signal(''),
      filteredConversations: signal([]),
      hasConversations: signal(false),
      totalUnreadCount: signal(0),
      loadConversations: vi.fn(),
      setSearchQuery: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [Home],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthStore, useValue: mockAuthStore },
        { provide: ConversationStore, useValue: mockConversationStore },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Home);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should load conversations on init', () => {
    expect(mockConversationStore.loadConversations).toHaveBeenCalled();
  });

  it('should display user initial', () => {
    expect(component.userInitial).toBe('T');
  });

  it('should toggle user menu', () => {
    expect(component.showUserMenu).toBe(false);
    component.toggleUserMenu();
    expect(component.showUserMenu).toBe(true);
    component.toggleUserMenu();
    expect(component.showUserMenu).toBe(false);
  });

  it('should call logout on authStore', () => {
    component.logout();
    expect(mockAuthStore.logout).toHaveBeenCalled();
  });

  it('should update search query', () => {
    component.onSearchChange('test');
    expect(mockConversationStore.setSearchQuery).toHaveBeenCalledWith('test');
  });

  it('should format time correctly', () => {
    const now = new Date();
    expect(component.formatTime(now)).toBe('now');

    const fiveMinutesAgo = new Date(now.getTime() - 5 * 60 * 1000);
    expect(component.formatTime(fiveMinutesAgo)).toBe('5m');

    const twoHoursAgo = new Date(now.getTime() - 2 * 60 * 60 * 1000);
    expect(component.formatTime(twoHoursAgo)).toBe('2h');

    const threeDaysAgo = new Date(now.getTime() - 3 * 24 * 60 * 60 * 1000);
    expect(component.formatTime(threeDaysAgo)).toBe('3d');
  });
});
