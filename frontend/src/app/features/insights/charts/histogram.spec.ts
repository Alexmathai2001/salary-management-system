import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DistributionBucket } from '../../../core/models';
import { Histogram } from './histogram';

const bucket = (from: number, count: number): DistributionBucket => ({ fromUsd: from, toUsd: from + 20_000, count });

describe('Histogram', () => {
  let fixture: ComponentFixture<Histogram>;
  const root = () => fixture.nativeElement as HTMLElement;
  const cols = () => Array.from(root().querySelectorAll<HTMLElement>('.col'));

  function render(buckets: DistributionBucket[]): void {
    fixture.componentRef.setInput('buckets', buckets);
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [Histogram] }).compileComponents();
    fixture = TestBed.createComponent(Histogram);
    fixture.componentRef.setInput('ariaLabel', 'Employees by pay band');
    fixture.componentRef.setInput('formatUsd', (usd: number) => `$${usd / 1000}K`);
  });

  it('draws one column per bucket, heights proportional to the count', () => {
    render([bucket(0, 100), bucket(20_000, 50), bucket(40_000, 0)]);

    const heights = cols().map((c) => c.querySelector<HTMLElement>('.bar')!.style.height);
    expect(heights).toEqual(['100%', '50%', '0%']);
  });

  it('labels the bin boundaries, not the bin centres', () => {
    render([bucket(0, 3), bucket(20_000, 5), bucket(40_000, 2)]);

    const labels = Array.from(root().querySelectorAll('.xtick')).map((t) => t.textContent);
    expect(labels).toEqual(['$0K', '$20K', '$40K', '$60K']); // 3 bins have 4 boundaries
  });

  it('thins the labels when there are many bins', () => {
    render(Array.from({ length: 40 }, (_, i) => bucket(i * 20_000, 1)));

    const labels = root().querySelectorAll('.xtick');
    expect(labels.length).toBeLessThanOrEqual(9);
    expect(labels.length).toBeGreaterThan(3);
  });

  it('uses clean y-axis ticks that cover the largest bucket', () => {
    render([bucket(0, 870), bucket(20_000, 120)]);

    const ticks = Array.from(root().querySelectorAll('.ytick')).map((t) => t.textContent);
    expect(ticks[0]).toBe('0');
    expect(Number(ticks[ticks.length - 1].replace(/,/g, ''))).toBeGreaterThanOrEqual(870);
  });

  it('shows the count and range on hover and on keyboard focus', () => {
    render([bucket(0, 1_234), bucket(20_000, 5)]);

    cols()[0].dispatchEvent(new Event('pointerenter'));
    fixture.detectChanges();
    const tip = root().querySelector('[role="tooltip"]')!;
    expect(tip.textContent).toContain('1,234 employees');
    expect(tip.textContent).toContain('$0K to $20K');

    cols()[0].dispatchEvent(new Event('pointerleave'));
    fixture.detectChanges();
    expect(root().querySelector('[role="tooltip"]')).toBeNull();

    cols()[1].dispatchEvent(new Event('focus'));
    fixture.detectChanges();
    expect(root().querySelector('[role="tooltip"]')!.textContent).toContain('$20K to $40K');
  });

  it('makes every column reachable and named for assistive technology', () => {
    render([bucket(0, 7)]);

    expect(cols()[0].getAttribute('tabindex')).toBe('0');
    expect(cols()[0].getAttribute('aria-label')).toBe('$0K to $20K: 7 employees');
  });

  it('renders nothing but the frame when empty', () => {
    render([]);

    expect(cols()).toHaveLength(0);
    expect(root().querySelectorAll('.xtick')).toHaveLength(0);
  });
});
