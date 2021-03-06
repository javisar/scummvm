/* ScummVM - Graphic Adventure Engine
 *
 * ScummVM is the legal property of its developers, whose names
 * are too numerous to list here. Please refer to the COPYRIGHT
 * file distributed with this source distribution.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 */

#ifndef ZVISION_SEARCH_MANAGER_H
#define ZVISION_SEARCH_MANAGER_H

#include "common/str.h"
#include "common/hash-str.h"
#include "common/hashmap.h"
#include "common/archive.h"
#include "common/file.h"
#include "common/list.h"

namespace ZVision {

class SearchManager {
public:
	SearchManager(const Common::String &rootPath, int depth);
	~SearchManager();

	void addFile(const Common::String &name, Common::Archive *arch);
	void addDir(const Common::String &name);
	void addPatch(const Common::String &src, const Common::String &dst);

	Common::File *openFile(const Common::String &name);
	bool openFile(Common::File &file, const Common::String &name);
	bool hasFile(const Common::String &name);

	void loadZix(const Common::String &name);

private:

	void listDirRecursive(Common::List<Common::String> &dirList, const Common::FSNode &fsNode, int depth);

	struct Node {
		Common::String name;
		Common::Archive *arch;
	};

	Common::List<Common::String> dirList;

	typedef Common::HashMap<Common::String, Node, Common::IgnoreCase_Hash, Common::IgnoreCase_EqualTo> MatchList;

	Common::List<Common::Archive *> archList;
	MatchList files;

	Common::String _root;

private:
};

}

#endif // ZVISION_SEARCH_MANAGER_H
