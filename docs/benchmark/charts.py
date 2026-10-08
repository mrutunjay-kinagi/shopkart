import json, sys
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
r = json.load(open(sys.argv[1])); out = sys.argv[2]
plt.rcParams.update({"font.family": "Times New Roman", "font.size": 11})
BEFORE, AFTER = "#9AA5B1", "#1F5FA8"
# search
q = [s["query"] for s in r["search"]]
like = [s["like"]["p50Ms"] for s in r["search"]]; ft = [s["fulltext"]["p50Ms"] for s in r["search"]]
fig, ax = plt.subplots(figsize=(7.5, 3.6), dpi=200)
x = range(len(q)); w = 0.38
b1 = ax.bar([i - w/2 for i in x], like, w, label="LIKE '%term%' (full table scan)", color=BEFORE)
b2 = ax.bar([i + w/2 for i in x], ft, w, label="InnoDB FULLTEXT (boolean mode)", color=AFTER)
for b in list(b1) + list(b2):
    ax.text(b.get_x() + b.get_width()/2, b.get_height() + 2, f"{b.get_height():.0f}", ha="center", fontsize=9)
ax.set_xticks(list(x)); ax.set_xticklabels([f'"{s}"' for s in q])
ax.set_ylabel("Median latency (ms)"); ax.set_title("Keyword search over 100,000 products (p50, lower is better)"); ax.set_ylim(0, max(like) * 1.35)
ax.spines[["top", "right"]].set_visible(False); ax.legend(frameon=False)
fig.tight_layout(); fig.savefig(f"{out}/15-bench-search.png")
# cache + index
c = r["productCache"]; cb = r["categoryBrowse"]
labels = ["Product page\n(HTTP, p50)", "Product lookup\n(service, p50)", "Category browse\n(SQL, p50)"]
before = [c["httpCacheMiss"]["p50Ms"], c["serviceMySql"]["p50Ms"], cb["withoutIndex"]["p50Ms"]]
after = [c["httpCacheHit"]["p50Ms"], c["serviceRedis"]["p50Ms"], cb["withIndex"]["p50Ms"]]
fig, ax = plt.subplots(figsize=(7.5, 3.4), dpi=200)
x = range(3)
b1 = ax.bar([i - w/2 for i in x], before, w, label="Before (MySQL, no cache / no index)", color=BEFORE)
b2 = ax.bar([i + w/2 for i in x], after, w, label="After (Redis cache / composite index)", color=AFTER)
for b in list(b1) + list(b2):
    ax.text(b.get_x() + b.get_width()/2, b.get_height() + 0.3, f"{b.get_height():.2f}", ha="center", fontsize=9)
ax.set_xticks(list(x)); ax.set_xticklabels(labels)
ax.set_ylabel("Median latency (ms)"); ax.set_title("Caching and indexing (lower is better)"); ax.set_ylim(0, max(before) * 1.3)
ax.spines[["top", "right"]].set_visible(False); ax.legend(frameon=False)
fig.tight_layout(); fig.savefig(f"{out}/16-bench-cache-index.png")
