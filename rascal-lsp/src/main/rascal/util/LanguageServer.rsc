/*
 * Copyright (c) 2018-2021, NWO-I CWI and Swat.engineering
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
@license{
  Copyright (c) 2021 NWO-I Centrum Wiskunde & Informatica
  All rights reserved. This program and the accompanying materials
  are made available under the terms of the Eclipse Public License v1.0
  which accompanies this distribution, and is available at
  http://www.eclipse.org/legal/epl-v10.html
}
@contributor{Jurgen J. Vinju - Jurgen.Vinju@cwi.nl - CWI}
module util::LanguageServer

import util::Reflective;
import util::Maybe;

import ParseTree;

data Language
    = language(PathConfig pcfg, str name, str extension, str mainModule, str mainFunction);

alias Parser           = Tree (str /*input*/, loc /*origin*/);
alias Summarizer       = Summary (loc /*origin*/, Tree /*input*/);
alias Outliner         = list[DocumentSymbol] (Tree /*input*/);
alias Completer        = list[Completion] (Tree /*input*/, str /*prefix*/, int /*requestOffset*/);
alias Builder          = list[Message] (list[loc] /*sources*/, PathConfig /*pcfg*/);
alias LensDetector     = rel[loc src, Command lens] (Tree /*input*/);
alias CommandExecutor  = void (Command /*command*/);
alias InlayHinter      = list[InlayHint] (Tree /*input*/);

// a decorator returns the current decorations for this tree,
// per invocation you have to repeat all styling for this file,
// otherwise they are cleared.
alias Decorator        = map[str baseStyle, lrel[loc range, Maybe[MarkdownString] hoverMessage, set[InstanceDecoration] extraDecorations]] (Tree /*input*/);

@synopsis{Each kind of service contibutes the implementation of one (or several) IDE features.}
data LanguageService
    = parser(Parser parser)
    | summarizer(Summarizer summarizer)
    | outliner(Outliner outliner)
    | completer(Completer completer)
    | builder(Builder builder)
    | lenses(LensDetector detector)
    | inlayHinter(InlayHinter hinter)
    | executor(CommandExecutor executor)
    | decorator(map[str styleCategory, set[Decoration] definitions] baseStyles, Decorator decorator)
    ;

@synopsis{A model encodes all IDE-relevant information about a single source file.}
data Summary = summary(loc src,
    rel[loc, Message] messages = {},
    rel[loc, str]     documentation = {},   // documentation for each location
    rel[loc, loc]     definitions = {},     // links to the definitions of names
    rel[loc, loc]     references = {},      // links to the uses of definitions
    rel[loc, loc]     implementations = {}  // links to the implementations of declarations
);

data Completion = completion(str newText, str proposal=newText);

@synopsis{DocumentSymbol encodes a sorted and hierarchical outline of a source file}
data DocumentSymbol
    = symbol(
        str name,
        DocumentSymbolKind kind,
        loc range,
        loc selection=range,
        str detail="",
        list[DocumentSymbol] children=[]
    );

data DocumentSymbolKind
	= \file()
	| \module()
	| \namespace()
	| \package()
	| \class()
	| \method()
	| \property()
	| \field()
	| \constructor()
	| \enum()
	| \interface()
	| \function()
	| \variable()
	| \constant()
	| \string()
	| \number()
	| \boolean()
	| \array()
	| \object()
	| \key()
	| \null()
	| \enumMember()
	| \struct()
	| \event()
	| \operator()
	| \typeParameter()
    ;

data DocumentSymbolTag
    = \deprecated()
    ;

data CompletionProposal = sourceProposal(str newText, str proposal=newText);

data Command(str title="")
    = noop()
    ;

data InlayHint
    = hint(
        loc range, // should point to a cursor position (begin and end column same)
        str label, // text that should be printed in the ide, spaces in front and back of the text are trimmed and turned into subtle spacing to the content around it.
        InlayKind kind,
        bool before = false // if the hint precedes or follows the text it's hinting
    );

data InlayKind // this determines style
    = \type()
    | parameter()
    | other(str name)
    ;

// Decorations are a subset of css styling, in general you want to define
// a standardStyle, an optionally defined overrides for dark and light themes
data Decoration
    = standardStyle(set[DecoratorOptions] options)
    | darkStyleOverrides(set[DecoratorOptions] options)
    | lightStyleOverrides(set[DecoratorOptions] options)
    | isWholeLine(bool continueRenderingAfterLineEnding)
    ;

// limited set of operations are possible to be overrideable per range in the tree
// mirrors https://code.visualstudio.com/api/references/vscode-api#DecorationInstanceRenderOptions
data InstanceDecoration
    = instanceStandard(set[InstanceDecorationOptions] options)
    | instanceDarkOverrides(set[InstanceDecorationOptions] options)
    | instanceLightOverrides(set[InstanceDecorationOptions] options)
    ;

data InstanceDecorationOptions
    = instanceAfter(AttachableDecorationOptions tdaro)
    | instanceBefore(AttachableDecorationOptions tdaro)
    ;

// css properties that decorators can change (in relation to the active theme and other decorations)
// mirrors: https://code.visualstudio.com/api/references/vscode-api#ThemableDecorationRenderOptions
data DecoratorOptions
    // option to insert content after & before the decorated text
    = after(AttachableDecorationOptions tdaro)
    | before(AttachableDecorationOptions tdaro)

    | backgroundColor(Color col)
    | color(Color col)
    | overviewRulerColor(Color col) // use transparant colors
    | opacity(str opacity)

    | border(str style)
    | borderColor(Color col)
    // subparts of border, useful for only overriding a single aspect of a parent definition
    // try to prefer generic border defititions
    | borderRadius(str style)
    | borderSpacing(str style)
    | borderStyle(str style)
    | borderWidth(str width)

    | outline(str style)
    | outlineColor(Color col)
    // subparts of outline, if possible, use outline.
    | outlineStyle(str style)
    | outlineWidth(str style)

    | cursor(str cursor)
    | fontStyle(str style)
    | fontWeight(str style)
    | letterSpacing(str spacing)
    | textDecoration(str style)


    // add custom gutter icons (area left of the line numbers)
    | gutterIconPath(loc iconPath)
    | gutterIconSize(GutterIconSize size)
;

// mirrors https://code.visualstudio.com/api/references/vscode-api#MarkdownString
data MarkdownString (
    bool isTrusted = false,
    bool supportHtml = false,
    bool supportThemeIconts = false,
    loc baseUri = |invalid:///|)
    = markdown(str content)
    ;

data GutterIconSize
    = auto()
    | contain()
    | cover()
    | percentage(real n)
    ;

data Color
    = htmlColor(str cssColor)
    | themeColor(str colorId) // https://code.visualstudio.com/docs/getstarted/theme-color-reference
    ;

// mirroring https://code.visualstudio.com/api/references/vscode-api#ThemableDecorationAttachmentRenderOptions
data AttachableDecorationOptions
    = attachBackgroundColor(Color col)
    | attachColor(Color col)

    | attachBorder(str style)
    | attachBorderColor(Color col)

    // only an icon or a text is allowed, not both
    | attachContentIconPath(loc iconToRender)
    | attachContentText(str text)

    | attachFontStyle(str style)
    | attachFontWeight(str style)
    | attachTextDecoration(str style)

    // you get a bit more control of the attachments
    | attachHeight(str height)
    | attachWidth(str width)
    | attachMargin(str margin)
;



@javaClass{org.rascalmpl.vscode.lsp.parametric.RascalInterface}
java void registerLanguage(Language lang);
