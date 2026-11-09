import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BarDatum, RankedBars } from './ranked-bars';

const data: BarDatum[] = [
  { label: 'United States', value: 100, detail: ['1,924 employees', 'Average: $90'] },
  { label: 'India', value: 50, detail: ['2,272 employees'] },
  { label: 'Brazil', value: 25, detail: ['496 employees'] },
];

describe('RankedBars', () => {
  let fixture: ComponentFixture<RankedBars>;
  const root = () => fixture.nativeElement as HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [RankedBars] }).compileComponents();
    fixture = TestBed.createComponent(RankedBars);
    fixture.componentRef.setInput('data', data);
    fixture.componentRef.setInput('ariaLabel', 'Median pay by country');
    fixture.componentRef.setInput('formatValue', (v: number) => `$${v}`);
    fixture.componentRef.setInput('formatTick', (v: number) => `${v}`);
    fixture.detectChanges();
  });

  const rows = () => Array.from(root().querySelectorAll<HTMLElement>('.row'));

  it('draws one bar per item, in the order given, with the value at the tip', () => {
    expect(rows().map((r) => r.querySelector('.label')!.textContent)).toEqual(['United States', 'India', 'Brazil']);
    expect(rows().map((r) => r.querySelector('.value')!.textContent)).toEqual(['$100', '$50', '$25']);
  });

  it('scales bar length to the value on a zero-based axis', () => {
    const widths = rows().map((r) => r.querySelector<HTMLElement>('.bar')!.style.width);

    expect(widths).toEqual(['100%', '50%', '25%']);
  });

  it('draws gridlines and tick labels at clean values', () => {
    const ticks = Array.from(root().querySelectorAll('.tick')).map((t) => t.textContent);

    expect(ticks).toEqual(['0', '20', '40', '60', '80', '100']);
    expect(root().querySelectorAll('.gridline')).toHaveLength(6);
  });

  it('names the chart and each bar for assistive technology', () => {
    expect(root().querySelector('[role="list"]')!.getAttribute('aria-label')).toBe('Median pay by country');
    expect(rows()[1].getAttribute('aria-label')).toBe('India: $50. 2,272 employees');
  });

  it('shows the details tooltip on hover and hides it on leave', () => {
    expect(root().querySelector('[role="tooltip"]')).toBeNull();

    rows()[0].dispatchEvent(new Event('pointerenter'));
    fixture.detectChanges();

    const tip = root().querySelector('[role="tooltip"]')!;
    expect(tip.textContent).toContain('$100');
    expect(tip.textContent).toContain('United States');
    expect(tip.textContent).toContain('1,924 employees');
    expect(tip.textContent).toContain('Average: $90');

    rows()[0].dispatchEvent(new Event('pointerleave'));
    fixture.detectChanges();
    expect(root().querySelector('[role="tooltip"]')).toBeNull();
  });

  it('shows the same tooltip for keyboard focus', () => {
    rows()[2].dispatchEvent(new Event('focus'));
    fixture.detectChanges();

    expect(root().querySelector('[role="tooltip"]')!.textContent).toContain('Brazil');
    expect(rows()[2].getAttribute('tabindex')).toBe('0');
  });

  it('treats text as text, never as markup', () => {
    fixture.componentRef.setInput('data', [{ label: '<img src=x onerror=alert(1)>', value: 10, detail: ['<b>x</b>'] }]);
    fixture.detectChanges();
    rows()[0].dispatchEvent(new Event('pointerenter'));
    fixture.detectChanges();

    expect(root().querySelector('img')).toBeNull();
    expect(root().querySelector('b')).toBeNull();
    expect(root().querySelector('.label')!.textContent).toBe('<img src=x onerror=alert(1)>');
  });

  it('copes with no data', () => {
    fixture.componentRef.setInput('data', []);
    fixture.detectChanges();

    expect(rows()).toHaveLength(0);
    expect(root().querySelectorAll('.tick').length).toBeGreaterThan(0);
  });
});
