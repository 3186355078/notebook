#!/usr/bin/env python3
"""Build non-sensitive adversarial Stage 9 backup fixtures from a valid archive."""

from __future__ import annotations

import argparse
import copy
import hashlib
import json
import warnings
import zipfile
from pathlib import Path


MANIFEST = "manifest.json"
ENTRIES = "data/work_entries.json"
BLOCKS = "data/content_blocks.json"
ATTACHMENTS = "data/attachments.json"
IMAGE = "attachments/images/stage9-saf.png"


def encode_json(value: object) -> bytes:
    return json.dumps(value, ensure_ascii=False, separators=(",", ":")).encode("utf-8")


def read_archive(path: Path) -> list[tuple[str, bytes, bool]]:
    with zipfile.ZipFile(path) as archive:
        return [
            (entry.filename, archive.read(entry), entry.is_dir())
            for entry in archive.infolist()
        ]


def values(entries: list[tuple[str, bytes, bool]]) -> dict[str, bytes]:
    return {name: data for name, data, is_directory in entries if not is_directory}


def replace(entries: list[tuple[str, bytes, bool]], name: str, data: bytes) -> None:
    for index, (candidate, _, is_directory) in enumerate(entries):
        if candidate == name and not is_directory:
            entries[index] = (name, data, False)
            return
    raise KeyError(name)


def remove(entries: list[tuple[str, bytes, bool]], name: str) -> None:
    entries[:] = [entry for entry in entries if entry[0] != name]


def update_declared_file(
    entries: list[tuple[str, bytes, bool]],
    path: str,
    content: bytes,
) -> None:
    manifest = json.loads(values(entries)[MANIFEST])
    declared = next(item for item in manifest["files"] if item["path"] == path)
    declared["size"] = len(content)
    declared["sha256"] = hashlib.sha256(content).hexdigest()
    replace(entries, MANIFEST, encode_json(manifest))


def replace_declared_json(
    entries: list[tuple[str, bytes, bool]],
    path: str,
    value: object,
) -> None:
    content = encode_json(value)
    replace(entries, path, content)
    update_declared_file(entries, path, content)


def write_archive(path: Path, entries: list[tuple[str, bytes, bool]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with warnings.catch_warnings():
        warnings.simplefilter("ignore", UserWarning)
        with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
            for name, data, is_directory in entries:
                entry_name = name if not is_directory or name.endswith("/") else f"{name}/"
                archive.writestr(entry_name, b"" if is_directory else data)


def fixture_manifest_truncated(base: list[tuple[str, bytes, bool]]) -> list[tuple[str, bytes, bool]]:
    replace(base, MANIFEST, b'{"formatName":"worklog-ai-backup"')
    return base


def mutate_manifest(base: list[tuple[str, bytes, bool]], key: str, value: object) -> list[tuple[str, bytes, bool]]:
    manifest = json.loads(values(base)[MANIFEST])
    manifest[key] = value
    replace(base, MANIFEST, encode_json(manifest))
    return base


def fixture_sha(base: list[tuple[str, bytes, bool]], path: str) -> list[tuple[str, bytes, bool]]:
    manifest = json.loads(values(base)[MANIFEST])
    next(item for item in manifest["files"] if item["path"] == path)["sha256"] = "0" * 64
    replace(base, MANIFEST, encode_json(manifest))
    return base


def fixture_size(base: list[tuple[str, bytes, bool]]) -> list[tuple[str, bytes, bool]]:
    manifest = json.loads(values(base)[MANIFEST])
    next(item for item in manifest["files"] if item["path"] == ENTRIES)["size"] += 1
    replace(base, MANIFEST, encode_json(manifest))
    return base


def fixture_duplicate_date(base: list[tuple[str, bytes, bool]]) -> list[tuple[str, bytes, bool]]:
    work_entries = json.loads(values(base)[ENTRIES])
    duplicate = dict(work_entries[0])
    duplicate["id"] = "stage9-duplicate-entry"
    work_entries.append(duplicate)
    replace_declared_json(base, ENTRIES, work_entries)
    manifest = json.loads(values(base)[MANIFEST])
    manifest["entryCount"] = len(work_entries)
    replace(base, MANIFEST, encode_json(manifest))
    return base


def fixture_orphan_block(base: list[tuple[str, bytes, bool]]) -> list[tuple[str, bytes, bool]]:
    blocks = json.loads(values(base)[BLOCKS])
    blocks[0]["entryId"] = "missing-stage9-entry"
    replace_declared_json(base, BLOCKS, blocks)
    return base


def fixture_invalid_table(base: list[tuple[str, bytes, bool]]) -> list[tuple[str, bytes, bool]]:
    blocks = json.loads(values(base)[BLOCKS])
    table = next(block for block in blocks if block["blockType"] == "TABLE")
    table["structuredContent"] = "{not-valid-table-json"
    replace_declared_json(base, BLOCKS, blocks)
    return base


def fixture_attachment_path(base: list[tuple[str, bytes, bool]]) -> list[tuple[str, bytes, bool]]:
    attachments = json.loads(values(base)[ATTACHMENTS])
    attachments[0]["localPath"] = "images/../../stage9-evil.png"
    replace_declared_json(base, ATTACHMENTS, attachments)
    return base


def fixture_attachment_mime(base: list[tuple[str, bytes, bool]]) -> list[tuple[str, bytes, bool]]:
    attachments = json.loads(values(base)[ATTACHMENTS])
    attachments[0]["mimeType"] = "image/jpeg"
    replace_declared_json(base, ATTACHMENTS, attachments)
    return base


def fixture_fake_image(base: list[tuple[str, bytes, bool]]) -> list[tuple[str, bytes, bool]]:
    fake = b"not a real PNG image"
    replace(base, IMAGE, fake)
    update_declared_file(base, IMAGE, fake)
    attachments = json.loads(values(base)[ATTACHMENTS])
    attachments[0]["fileSize"] = len(fake)
    replace_declared_json(base, ATTACHMENTS, attachments)
    return base


def fixture_high_ratio(base: list[tuple[str, bytes, bool]]) -> list[tuple[str, bytes, bool]]:
    base.append(("attachments/images/stage9-ratio.bin", b"0" * (2 * 1024 * 1024), False))
    return base


def fixture_single_entry_limit(base: list[tuple[str, bytes, bool]]) -> list[tuple[str, bytes, bool]]:
    base.append(("attachments/images/stage9-oversize.bin", b"0" * (100 * 1024 * 1024 + 1), False))
    return base


def fixture_entry_count(base: list[tuple[str, bytes, bool]]) -> list[tuple[str, bytes, bool]]:
    # The production limit is 100,000 entries. Keep contents empty so the fixture stays compact.
    return [(MANIFEST, values(base)[MANIFEST], False)] + [
        (f"attachments/images/entry-{index:06d}.bin", b"", False)
        for index in range(100_000)
    ]


def build_fixtures(base_path: Path, output: Path) -> dict[str, str]:
    original = read_archive(base_path)

    factories = {
        "01-manifest-truncated.zip": fixture_manifest_truncated,
        "02-format-name.zip": lambda entries: mutate_manifest(entries, "formatName", "not-worklog-ai"),
        "03-future-version.zip": lambda entries: mutate_manifest(entries, "formatVersion", 2),
        "04-json-sha.zip": lambda entries: fixture_sha(entries, ENTRIES),
        "05-image-sha.zip": lambda entries: fixture_sha(entries, IMAGE),
        "06-size.zip": fixture_size,
        "07-missing-core-json.zip": lambda entries: (remove(entries, BLOCKS) or entries),
        "08-undeclared-file.zip": lambda entries: entries + [("data/extra.json", b"{}", False)],
        "09-duplicate-entry.zip": lambda entries: entries + [(MANIFEST, values(entries)[MANIFEST], False)],
        "10-zip-slip.zip": lambda entries: entries + [("../evil", b"stage9", False)],
        "11-drive-path.zip": lambda entries: entries + [("C:/evil", b"stage9", False)],
        "12-outside-whitelist.zip": lambda entries: entries + [("notes/readme.txt", b"stage9", False)],
        "13-duplicate-entry-date.zip": fixture_duplicate_date,
        "14-orphan-content-block.zip": fixture_orphan_block,
        "15-invalid-table.zip": fixture_invalid_table,
        "16-invalid-attachment-path.zip": fixture_attachment_path,
        "17-image-mime-mismatch.zip": fixture_attachment_mime,
        "18-fake-image.zip": fixture_fake_image,
        "19-entry-count-limit.zip": fixture_entry_count,
        "20-single-entry-limit.zip": fixture_single_entry_limit,
        "21-high-compression-ratio.zip": fixture_high_ratio,
    }

    output.mkdir(parents=True, exist_ok=True)
    result: dict[str, str] = {}
    for filename, factory in factories.items():
        entries = factory(copy.deepcopy(original))
        target = output / filename
        write_archive(target, entries)
        result[filename] = hashlib.sha256(target.read_bytes()).hexdigest()

    (output / "SHA256SUMS.json").write_text(
        json.dumps(result, indent=2, sort_keys=True),
        encoding="utf-8",
    )
    return result


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("base", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    fixtures = build_fixtures(args.base, args.output)
    print(f"Generated {len(fixtures)} non-sensitive fixtures in {args.output}")


if __name__ == "__main__":
    main()
