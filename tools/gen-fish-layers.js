/*
 * gen-fish-layers.js —— 从 tools/fish-layers.json 生成 FishLayers.java。
 *
 * 与 gen-fish-habits.js 同一套理由：**json 是唯一真相**，Java 是生成物。
 * 手写一张"47 鱼 → 水层"的表，改一处忘一处，而忘掉的表现是"某层的鱼悄悄变少/变多"，
 * 没有任何报错 —— 那正是这个仓库最怕的那类 bug（不崩不报，只是数值不对）。
 *
 * 用法：
 *   node tools/gen-fish-layers.js            # 只预览
 *   node tools/gen-fish-layers.js --write    # 写入 FishLayers.java
 */
const fs = require('fs');
const path = require('path');

const ROOT = process.env.ATL_ROOT ? path.resolve(process.env.ATL_ROOT) : path.join(__dirname, '..');
const SRC = path.join(ROOT, 'tools', 'fish-layers.json');
const OUT = path.join(ROOT, 'src/main/java/com/whitecobalt/anglertackle/fishing/FishLayers.java');

const data = JSON.parse(fs.readFileSync(SRC, 'utf8'));

/** 枚举常量名 → id（顺序即位序，不许重排）。 */
const ENUM = (id) => id.toUpperCase();

function render() {
  const lines = [];
  lines.push('package com.whitecobalt.anglertackle.fishing;');
  lines.push('');
  lines.push('/*');
  lines.push(' * ⚠ 本文件由 tools/gen-fish-layers.js 从 tools/fish-layers.json **生成**，不要手改。');
  lines.push(' *   要改某条鱼的水层：改那个 json，然后跑');
  lines.push(' *     node tools/gen-fish-layers.js --write');
  lines.push(' *   tools/check-fish-layers.js 会把本文件与生成器的输出逐字节比对，手改必红。');
  lines.push(' *');
  lines.push(' * 依据：仓库内 fish_research/<id>.json 的 waterLayer 字段（每条带来源 URL），');
  lines.push(' *   判定口径见 docs/42-铅锤系统策划案.md §3.3。');
  lines.push(' */');
  lines.push('public final class FishLayers {');
  lines.push('');
  lines.push('    private FishLayers() {');
  lines.push('    }');
  lines.push('');
  lines.push('    /**');
  lines.push('     * 水层。**顺序即位序**（{@link #bit()}），不许重排 —— 铅锤倍率按位查表。');
  lines.push('     */');
  lines.push('    public enum Layer {');
  for (const l of data.layers) {
    lines.push('        /** ' + l.zh + ' */');
    lines.push('        ' + ENUM(l.id) + '("' + l.id + '"),');
  }
  // 去掉最后一个逗号
  lines[lines.length - 1] = lines[lines.length - 1].replace(/,$/, ';');
  lines.push('');
  lines.push('        private final String id;');
  lines.push('');
  lines.push('        Layer(String id) {');
  lines.push('            this.id = id;');
  lines.push('        }');
  lines.push('');
  lines.push('        /** ASCII 数据键（配置 / 存档 / 生成器都用它）。 */');
  lines.push('        public String id() {');
  lines.push('            return this.id;');
  lines.push('        }');
  lines.push('');
  lines.push('        /** 位序：{@code 1 << ordinal()}，供掩码用。 */');
  lines.push('        public int bit() {');
  lines.push('            return 1 << this.ordinal();');
  lines.push('        }');
  lines.push('');
  lines.push('        /** 语言键：{@code layer.anglertackle.<id>}。 */');
  lines.push('        public String langKey() {');
  lines.push('            return "layer.anglertackle." + this.id;');
  lines.push('        }');
  lines.push('    }');
  lines.push('');
  lines.push('    /** 鱼 id → 水层。找不到时返回 {@code null}（调用方自己决定兜底，绝不静默当中层）。 */');
  lines.push('    private static final java.util.Map<String, Layer> FISH = new java.util.HashMap<>();');
  lines.push('');
  lines.push('    static {');
  for (const [fish, layer] of Object.entries(data.fish)) {
    lines.push('        FISH.put("' + fish + '", Layer.' + ENUM(layer) + ');');
  }
  lines.push('    }');
  lines.push('');
  lines.push('    /** 查一条鱼的水层；未知 id 返回 {@code null}。 */');
  lines.push('    public static Layer of(String fishId) {');
  lines.push('        return FISH.get(fishId);');
  lines.push('    }');
  lines.push('');
  lines.push('    /** 已知鱼种数（给自检用）。 */');
  lines.push('    public static int size() {');
  lines.push('        return FISH.size();');
  lines.push('    }');
  lines.push('}');
  lines.push('');
  return lines.join('\n');
}

const text = render();
// 被 require 时（检查器要拿 render()）不打印 CLI 输出
if (require.main === module) {
  if (process.argv.includes('--write')) {
  fs.writeFileSync(OUT, text, 'utf8');
  console.log('✅ FishLayers.java 已写：' + Object.keys(data.fish).length + ' 条鱼 / '
      + data.layers.length + ' 层');
} else {
  console.log('（预览，未写盘）' + Object.keys(data.fish).length + ' 条鱼 / ' + data.layers.length + ' 层');
  console.log('下一步：node tools/gen-fish-layers.js --write');
  }
}

module.exports = { render, OUT };
